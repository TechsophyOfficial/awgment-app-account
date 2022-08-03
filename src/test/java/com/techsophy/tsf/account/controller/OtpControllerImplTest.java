package com.techsophy.tsf.account.controller;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.OtpControllerImpl;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static com.techsophy.tsf.account.constants.ThemesConstants.TEST_ACTIVE_PROFILE;

@SpringBootTest
@ActiveProfiles(TEST_ACTIVE_PROFILE)
@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@AutoConfigureMockMvc(addFilters = false)
 class OtpControllerImplTest {
    @InjectMocks
    OtpControllerImpl otpControllerImpl;
    @Mock
    OtpService otpService;
    @Mock
    GlobalMessageSource globalMessageSource;

    @Test
    void generateOtp() {
        Map<String, String> templateData = new HashMap<>();
        templateData.put("data", "data2");
        templateData.put("data1", "data3");
        OtpRequestPayload otpRequestPayload = new OtpRequestPayload("channel", "to", "from", "subject", "cc", "body", "templateId", templateData);
        ApiResponse response = otpControllerImpl.generateOtp(otpRequestPayload);
        Assertions.assertNotNull(response);
    }

    @Test
    void verifyOtpTest() {
        OtpVerifyPayload otpVerifyPayload = new OtpVerifyPayload("channel", "to", "otp");
        ApiResponse response = otpControllerImpl.verifyOtp(otpVerifyPayload);
        Assertions.assertNotNull(response);
    }
}

