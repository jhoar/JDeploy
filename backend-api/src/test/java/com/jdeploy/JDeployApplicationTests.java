package com.jdeploy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = JDeployApplication.class,
        properties = {
                "jdeploy.security.users.ingest.username=ingest",
                "jdeploy.security.users.ingest.password=Ingest#Pass123",
                "jdeploy.security.users.generator.username=generator",
                "jdeploy.security.users.generator.password=Generator#Pass123",
                "jdeploy.security.users.reader.username=reader",
                "jdeploy.security.users.reader.password=Reader#Pass123"
        })
class JDeployApplicationTests {

    @Test
    void contextLoads() {
    }
}
