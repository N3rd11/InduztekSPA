package cl.induztek.protocol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"cl.induztek.protocol", "cl.induztek.common"})
public class ProtocolApplication {
    public static void main(String[] args) { SpringApplication.run(ProtocolApplication.class,args); }
}
