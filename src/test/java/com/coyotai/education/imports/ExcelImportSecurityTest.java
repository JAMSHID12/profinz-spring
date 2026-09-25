package com.coyotai.education.imports;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class ExcelImportSecurityTest {
    @Configuration @EnableMethodSecurity static class Config {
        @Bean ExcelImportService service(){return mock(ExcelImportService.class);}
        @Bean ExcelImportController controller(ExcelImportService service){return new ExcelImportController(service);}
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void nonAdminsCannotDownloadPreviewOrSaveEvenWithImportPermission(){
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            var controller=context.getBean(ExcelImportController.class);
            for(String role:List.of("FACULTY","MENTORS","STUDENTS","STAFF")) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test","",List.of(new SimpleGrantedAuthority("ROLE_"+role),new SimpleGrantedAuthority("STUDENT_CREATE"))));
                assertThatThrownBy(()->controller.template("students")).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(()->controller.preview("students",null,null)).isInstanceOf(AccessDeniedException.class);
                assertThatThrownBy(()->controller.save("students",null,"digest",null)).isInstanceOf(AccessDeniedException.class);
            }
            verifyNoInteractions(context.getBean(ExcelImportService.class));
        }
    }
    @Test void adminCanReachController(){
        try(var context=new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRATIVE"))));
            context.getBean(ExcelImportController.class).template("students");
            verify(context.getBean(ExcelImportService.class)).template(ImportKind.STUDENTS);
        }
    }
}
