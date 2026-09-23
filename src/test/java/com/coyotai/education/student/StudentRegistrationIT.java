package com.coyotai.education.student;

import com.coyotai.education.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StudentRegistrationIT extends IntegrationTestBase {
    @Autowired JdbcTemplate jdbc;

    private String request(long batch, String name) throws Exception {
        return json("fullName", name, "batchId", batch, "parentName", "Registration Parent",
                "parentPhoneNumber", "+919876543210", "parentWhatsappOptIn", true, "createLogin", true);
    }

    @Test void generatesIdsAndKeepsIdentityOnEditAndTransfer() throws Exception {
        String token = adminToken();
        long batch = batchId("CA Inter A");
        JsonNode first = data(postAs(token, "/api/students", request(batch, "Registration Test")).andExpect(status().isOk()));
        JsonNode second = data(postAs(token, "/api/students", request(batch, "Registration Test Two")).andExpect(status().isOk()));
        assertThat(first.path("admissionNumber").asText()).matches("ADM-[0-9]{4}-[0-9]{6}");
        assertThat(first.path("studentCode").asText()).matches("CAINTER-CAINTER-[0-9]{6}");
        assertThat(first.path("studentCode")).isNotEqualTo(second.path("studentCode"));
        assertThat(first.path("admissionNumber")).isEqualTo(first.path("username"));
        assertThat(first.path("parent").path("name").asText()).isEqualTo("Registration Parent");
        long id = first.path("id").asLong();
        JsonNode edited = data(putAs(token, "/api/students/{id}", request(batch, "Renamed Student"), id).andExpect(status().isOk()));
        assertThat(edited.path("admissionNumber")).isEqualTo(first.path("admissionNumber"));
        JsonNode transferred = data(postAs(token, "/api/students/{id}/transfer", json("batchId", batchId("CA Inter B")), id).andExpect(status().isOk()));
        assertThat(transferred.path("studentCode")).isEqualTo(first.path("studentCode"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='parents'", Integer.class)).isZero();
    }

    @Test void concurrentRegistrationsHaveUniqueIds() throws Exception {
        String token = adminToken();
        long batch = batchId("CA Inter A");
        ExecutorService workers = Executors.newFixedThreadPool(4);
        try {
            List<Callable<JsonNode>> tasks = new ArrayList<>();
            for (int i=0;i<4;i++) {
                String body=request(batch, "Concurrent Student " + i);
                tasks.add(() -> data(postAs(token,"/api/students",body).andExpect(status().isOk())));
            }
            Set<String> admissions=new HashSet<>(), codes=new HashSet<>();
            for (Future<JsonNode> result: workers.invokeAll(tasks)) {
                JsonNode row=result.get(30,TimeUnit.SECONDS);
                admissions.add(row.path("admissionNumber").asText());
                codes.add(row.path("studentCode").asText());
            }
            assertThat(admissions).hasSize(4);
            assertThat(codes).hasSize(4);
        } finally { workers.shutdownNow(); }
    }

    @Test void uploadsValidatedPrivatePhotoAndRejectsInvalidImageAtomically() throws Exception {
        String token=adminToken();
        long batch=batchId("CA Inter A");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(12,12,BufferedImage.TYPE_INT_RGB),"png",bytes);
        MockMultipartFile body=new MockMultipartFile("student","student.json","application/json",request(batch,"Photo Student").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockMultipartFile photo=new MockMultipartFile("photo","../../unsafe.png","image/png",bytes.toByteArray());
        JsonNode saved=data(mockMvc.perform(multipart("/api/students").file(body).file(photo).header("Authorization",token)).andExpect(status().isOk()));
        long id=saved.path("id").asLong();
        assertThat(saved.path("photoUrl").asText()).startsWith("/api/students/"+id+"/photo");
        mockMvc.perform(get("/api/students/{id}/photo",id).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(content().contentType("image/jpeg"));
        mockMvc.perform(get("/api/students/{id}/photo",id)).andExpect(status().isUnauthorized());
        getAs(login("mentor2","mentor123"),"/api/students/{id}/photo",id).andExpect(status().isForbidden());
        int count=jdbc.queryForObject("SELECT COUNT(*) FROM students",Integer.class);
        mockMvc.perform(multipart("/api/students").file(body)
                .file(new MockMultipartFile("photo","fake.png","image/png","not an image".getBytes()))
                .header("Authorization",token)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM students",Integer.class)).isEqualTo(count);
        mockMvc.perform(multipart("/api/students/{id}",id).file(body).with(req->{req.setMethod("PUT");return req;})
                .header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.photoUrl").value(saved.path("photoUrl").asText()));
    }

    @Test void validatesParentContactAndRequiresBatch() throws Exception {
        String token=adminToken();
        postAs(token,"/api/students",json("fullName","Missing Parent","batchId",batchId("CA Inter A")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.parentName").exists());
        postAs(token,"/api/students",json("fullName","No Batch","parentName","Parent","parentPhoneNumber","+919876543210"))
                .andExpect(status().isBadRequest());
    }
}
