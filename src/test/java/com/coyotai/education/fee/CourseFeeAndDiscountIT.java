package com.coyotai.education.fee;

import com.fasterxml.jackson.databind.JsonNode;
import com.coyotai.education.platform.ProjectConfigService;
import com.coyotai.education.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The course fee is master data; students differ only by their own discount.
 * Example from the centre: CMA USA fee 20,000; discounts of 2,000, 3,000 and 5,000.
 */
class CourseFeeAndDiscountIT extends IntegrationTestBase {

    @Autowired
    private ProjectConfigService config;

    @Test
    @DisplayName("Every student is billed the course fee less their own discount")
    void courseFeeWithPerStudentDiscounts() throws Exception {
        String admin = adminToken();
        Setup setup = course(admin, 20000, 2, 3);

        JsonNode result = data(postAs(admin, "/api/fees/plans/bulk", json(
                "batchId", setup.batchId,
                "firstDueDate", config.today().toString(),
                "discounts", List.of(
                        Map.of("studentId", setup.students.get(0), "discountAmount", 2000, "reason", "Financial hardship"),
                        Map.of("studentId", setup.students.get(1), "discountAmount", 3000, "reason", "Family income"),
                        Map.of("studentId", setup.students.get(2), "discountAmount", 5000, "reason", "Merit scholarship"))))
                .andExpect(status().isOk()));

        JsonNode created = result.path("created");
        assertThat(created).hasSize(3);
        assertThat(amounts(created, "totalAmount")).usingElementComparator(BigDecimal::compareTo)
                .containsOnly(new BigDecimal("20000"));
        assertThat(amounts(created, "netAmount")).usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("18000"), new BigDecimal("17000"), new BigDecimal("15000"));
        JsonNode first = find(created, "discountReason", "Financial hardship");
        // The course's default of two installments, each half of the fee after discount.
        assertThat(amounts(first.path("installments"), "amount")).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("9000"), new BigDecimal("9000"));

        // Running it again changes nothing: each student already has a plan for the course and year.
        JsonNode again = data(postAs(admin, "/api/fees/plans/bulk", json("batchId", setup.batchId,
                "firstDueDate", config.today().toString())));
        assertThat(again.path("created")).isEmpty();
        assertThat(again.path("skipped").asInt()).isEqualTo(3);
        postAs(admin, "/api/fees/plans", json("studentId", setup.students.get(0), "firstDueDate", config.today().toString()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("A course without a fee in the master data cannot be billed")
    void courseFeeRequired() throws Exception {
        String admin = adminToken();
        Setup setup = course(admin, null, null, 1);
        postAs(admin, "/api/fees/plans", json("studentId", setup.students.get(0), "firstDueDate", config.today().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Set the fee for")));
    }

    @Test
    @DisplayName("A discount can change later without touching what was paid; fee changes apply to new plans only")
    void discountChangesLater() throws Exception {
        String admin = adminToken();
        Setup setup = course(admin, 20000, 2, 1);
        long student = setup.students.get(0);

        JsonNode plan = data(postAs(admin, "/api/fees/plans", json("studentId", student,
                "firstDueDate", config.today().toString())).andExpect(status().isOk()));
        assertThat(plan.path("netAmount").decimalValue()).isEqualByComparingTo("20000");
        long planId = plan.path("id").asLong();
        long firstInstallment = plan.path("installments").get(0).path("id").asLong();

        postAs(admin, "/api/payments", json("installmentId", firstInstallment, "amount", 10000, "paymentMethod", "CASH"))
                .andExpect(status().isOk());

        // Financial situation changed: 3,000 off. Only the unpaid installment gets cheaper.
        JsonNode changed = data(putAs(admin, "/api/fees/plans/{id}/discount",
                json("discountAmount", 3000, "reason", "Financial hardship"), planId).andExpect(status().isOk()));
        assertThat(changed.path("netAmount").decimalValue()).isEqualByComparingTo("17000");
        assertThat(changed.path("discountReason").asText()).isEqualTo("Financial hardship");
        assertThat(amounts(changed.path("installments"), "amount")).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("10000"), new BigDecimal("7000"));
        assertThat(changed.path("paidAmount").decimalValue()).isEqualByComparingTo("10000");

        // 10,000 is already paid, so at most 10,000 can be discounted - and never more than the fee.
        putAs(admin, "/api/fees/plans/{id}/discount", json("discountAmount", 12000), planId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("at most 10000")));
        putAs(admin, "/api/fees/plans/{id}/discount", json("discountAmount", 25000), planId)
                .andExpect(status().isBadRequest());

        // A new course fee is for future plans; this student keeps the fee they were billed.
        putAs(admin, "/api/courses/{id}", json("code", setup.courseCode, "name", setup.courseCode,
                "feeAmount", 25000, "defaultInstallments", 2), setup.courseId).andExpect(status().isOk());
        JsonNode reloaded = data(getAs(admin, "/api/fees/plans/{id}", planId));
        assertThat(reloaded.path("totalAmount").decimalValue()).isEqualByComparingTo("20000");

        // Accounts can see it; the student sees their own discount in the portal.
        JsonNode summary = data(getAs(admin, "/api/fees/student/{id}", student));
        assertThat(summary.path("discount").decimalValue()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(summary.path("outstanding").decimalValue()).isEqualByComparingTo(new BigDecimal("7000"));
    }

    private record Setup(long courseId, String courseCode, long batchId, List<Long> students) {
    }

    private List<BigDecimal> amounts(JsonNode array, String field) {
        List<BigDecimal> amounts = new ArrayList<>();
        array.forEach(element -> amounts.add(element.path(field).decimalValue()));
        return amounts;
    }

    /** A fresh course (with or without a fee), a batch in the current year and some students. */
    private Setup course(String admin, Integer fee, Integer installments, int studentCount) throws Exception {
        String code = "FEE" + (System.nanoTime() % 1_000_000_000L);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", code);
        if (fee != null) {
            body.put("feeAmount", fee);
            body.put("defaultInstallments", installments);
        }
        long courseId = data(postAs(admin, "/api/courses", objectMapper.writeValueAsString(body))
                .andExpect(status().isOk())).path("id").asLong();
        long yearId = find(data(getAs(admin, "/api/academic-years")), "current", "true").path("id").asLong();
        long batchId = data(postAs(admin, "/api/batches", json("name", code + " A", "courseId", courseId,
                "academicYearId", yearId)).andExpect(status().isOk())).path("id").asLong();
        List<Long> students = new ArrayList<>();
        for (int i = 1; i <= studentCount; i++) {
            students.add(data(postAs(admin, "/api/students", json("parentName", "Fee Parent", "parentPhoneNumber", "+919876543210",
                    "fullName", "Fee Student " + i, "batchId", batchId)).andExpect(status().isOk())).path("id").asLong());
        }
        return new Setup(courseId, code, batchId, students);
    }
}
