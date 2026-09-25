package com.coyotai.education.imports;
import com.coyotai.education.academic.*;
import com.coyotai.education.auth.UserRepository;
import com.coyotai.education.platform.*;
import com.coyotai.education.staff.*;
import com.coyotai.education.student.*;
import com.coyotai.education.tenant.TenantIdentifierResolver;
import jakarta.persistence.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ExcelImportServiceTest {
    BatchRepository batches; StudentService students; ExcelFiles files; StaffService staff; UserRepository users; EntityManager em; ExcelImportService service;
    MockMultipartFile upload=new MockMultipartFile("file","test.xlsx","",new byte[]{1,2,3});
    @BeforeEach void setup(){
        batches=mock(BatchRepository.class);students=mock(StudentService.class);files=mock(ExcelFiles.class);staff=mock(StaffService.class);users=mock(UserRepository.class);em=mock(EntityManager.class);
        var config=mock(ProjectConfigService.class);when(config.isModuleEnabled(any())).thenReturn(true);
        var query=mock(Query.class);when(em.createNativeQuery(anyString())).thenReturn(query);when(query.setParameter(eq("id"),any())).thenReturn(query);
        service=new ExcelImportService(files,students,mock(StudentRepository.class),staff,mock(MentorRepository.class),mock(FacultyRepository.class),users,batches,mock(CourseRepository.class),mock(CourseService.class),Validation.buildDefaultValidatorFactory().getValidator(),em,mock(TenantIdentifierResolver.class),config);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVE"),new SimpleGrantedAuthority("MENTOR_MANAGE"))));
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    ExcelFiles.RowData row(int number,String username){
        Map<String,String> values=new LinkedHashMap<>();for(int i=0;i<ImportKind.MENTORS.headers.size();i++)values.put(ImportKind.MENTORS.headers.get(i),ImportKind.MENTORS.example.get(i));
        values.put("username",username);values.put("employee_code","M"+number);values.put("password","Private-initial-123");return new ExcelFiles.RowData(number,values);
    }
    @Test void previewMasksPasswordAndDoesNotWrite(){
        when(files.read(any(),any())).thenReturn(List.of(row(2,"new.mentor")));
        var result=service.preview(ImportKind.MENTORS,upload,null);
        assertThat(result.invalid()).isZero();assertThat(result.rows().get(0).values().get("password")).isEqualTo("••••••••");verifyNoInteractions(staff,em);
    }
    @Test void invalidRowBlocksAllWrites(){
        when(files.read(any(),any())).thenReturn(List.of(row(2,"valid.mentor"),row(3,"existing.mentor")));
        when(users.existsByUsernameIgnoreCase("existing.mentor")).thenReturn(true);
        var preview=service.preview(ImportKind.MENTORS,upload,null);var result=service.save(ImportKind.MENTORS,upload,preview.digest(),null);
        assertThat(result.invalid()).isEqualTo(1);assertThat(result.saved()).isZero();verifyNoInteractions(staff);verify(em,never()).flush();
    }
    @Test void saveRevalidatesAndRejectsChangedFile(){
        when(files.read(any(),any())).thenReturn(List.of(row(2,"new.mentor")));
        var preview=service.preview(ImportKind.MENTORS,upload,null);
        assertThatThrownBy(()->service.save(ImportKind.MENTORS,upload,"changed",null)).hasMessageContaining("changed");
        assertThat(service.save(ImportKind.MENTORS,upload,preview.digest(),null).saved()).isEqualTo(1);
        verify(staff).createMentor(any());verify(files,times(2)).read(any(),any());verify(em).flush();
    }
    @Test void duplicateWithinWorkbookRejected(){
        when(files.read(any(),any())).thenReturn(List.of(row(2,"same.mentor"),row(3,"same.mentor")));
        assertThat(service.preview(ImportKind.MENTORS,upload,null).invalid()).isEqualTo(1);
    }

    @Test void studentBatchIsSelectedOutsideWorkbookAndTechnicalFlagsDefaultFalse(){
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVE"),new SimpleGrantedAuthority("STUDENT_CREATE"))));
        Batch batch=mock(Batch.class);when(batch.getStatus()).thenReturn(Batch.Status.ACTIVE);
        when(batches.findDetail(12L)).thenReturn(Optional.of(batch));
        Map<String,String> values=new LinkedHashMap<>();for(int i=0;i<ImportKind.STUDENTS.headers.size();i++) values.put(ImportKind.STUDENTS.headers.get(i),ImportKind.STUDENTS.example.get(i));
        when(files.read(any(),any())).thenReturn(List.of(new ExcelFiles.RowData(2,values)));
        assertThatThrownBy(()->service.preview(ImportKind.STUDENTS,upload,null)).hasMessageContaining("Choose a batch");
        var preview=service.preview(ImportKind.STUDENTS,upload,12L);
        assertThat(preview.invalid()).isZero();
        assertThatThrownBy(()->service.save(ImportKind.STUDENTS,upload,preview.digest(),13L)).hasMessageContaining("changed");
        assertThat(service.save(ImportKind.STUDENTS,upload,preview.digest(),12L).saved()).isEqualTo(1);
        var request=org.mockito.ArgumentCaptor.forClass(StudentDtos.StudentRequest.class);
        verify(students).create(request.capture());
        assertThat(request.getValue().batchId()).isEqualTo(12L);
        assertThat(request.getValue().parentWhatsappOptIn()).isFalse();
        assertThat(request.getValue().createLogin()).isFalse();
        when(batch.getStatus()).thenReturn(Batch.Status.INACTIVE);
        assertThatThrownBy(()->service.preview(ImportKind.STUDENTS,upload,12L)).hasMessageContaining("active batch");
    }
}
