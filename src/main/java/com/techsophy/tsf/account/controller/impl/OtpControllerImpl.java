package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.OtpController;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import static com.techsophy.tsf.account.constants.AccountConstants.OTP_GENERATED_SUCCESSFULLY;
import static com.techsophy.tsf.account.constants.AccountConstants.OTP_VALIDATED_SUCCESSFULLY;

/**
 * Implementation of {@link OtpController} responsible for handling OTP-related operations.
 */
@RestController
@RequiredArgsConstructor
public class OtpControllerImpl implements OtpController {
    private final OtpService otpService;
    private final GlobalMessageSource globalMessageSource;

    /**
     * Generates an OTP for the given request payload.
     *
     * @param otpRequestPayload The payload containing user details for OTP generation.
     * @return ApiResponse indicating the success of OTP generation.
     * @throws JsonProcessingException If an error occurs during JSON processing.
     */
    @Override
    public ApiResponse<Void> generateOtp(OtpRequestPayload otpRequestPayload) throws JsonProcessingException {
        otpService.generateOtp(otpRequestPayload);
        return new ApiResponse<>(null, true, globalMessageSource.get(OTP_GENERATED_SUCCESSFULLY));
    }

    /**
     * Verifies the OTP provided by the user.
     *
     * @param otpVerifyPayload The payload containing OTP and user details for verification.
     * @return ApiResponse indicating the success of OTP verification.
     */
    @Override
    public ApiResponse<Void> verifyOtp(OtpVerifyPayload otpVerifyPayload) {
        otpService.verifyOtp(otpVerifyPayload);
        return new ApiResponse<>(null, true, globalMessageSource.get(OTP_VALIDATED_SUCCESSFULLY));
    }
}
