package com.techsophy.tsf.account.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.impl.OtpControllerImpl;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
 class OtpControllerImplTest {
    @InjectMocks
    OtpControllerImpl otpControllerImpl;
    @Mock
    OtpService otpService;
    @Mock
    GlobalMessageSource globalMessageSource;

    @Test
    void generateOtp() throws JsonProcessingException {
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

