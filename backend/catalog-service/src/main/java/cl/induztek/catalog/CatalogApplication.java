package cl.induztek.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cl.induztek.catalog", "cl.induztek.common"})
public class CatalogApplication {
    public static void main(String[] args) { SpringApplication.run(CatalogApplication.class,args); }
}
