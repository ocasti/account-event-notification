package co.cobre.simulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Clock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {SimulatorApplication.class})
@Import(SimulatorExceptionHandler.class)
class SimulatorControllerValidationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private EventGenerator generator;

    @MockitoBean
    private SqsEventPublisher publisher;

    @MockitoBean
    private Clock clock;

    @Test
    void postWithoutClientIdReturns400ValidationError() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        mockMvc.perform(post("/simulator/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "event_type": "credit_deposit",
                        "content": "x"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_error"));
    }
}
