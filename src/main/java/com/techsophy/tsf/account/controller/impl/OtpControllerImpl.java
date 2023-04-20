package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.OtpController;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.OtpService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;
import static com.techsophy.tsf.account.constants.AccountConstants.OTP_GENERATED_SUCCESSFULLY;
import static com.techsophy.tsf.account.constants.AccountConstants.OTP_VALIDATED_SUCCESSFULLY;

@RestController
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class OtpControllerImpl implements OtpController
{
    OtpService otpService;
    GlobalMessageSource globalMessageSource;

    @Override
    public ApiResponse<Void> generateOtp(OtpRequestPayload otpRequestPayload) throws JsonProcessingException {
      otpService.generateOtp(otpRequestPayload);
      return new ApiResponse<>(null,true,globalMessageSource.get(OTP_GENERATED_SUCCESSFULLY));
    }

    @Override
    public ApiResponse<Void> verifyOtp(OtpVerifyPayload otpVerifyPayload)
    {
        otpService.verifyOtp(otpVerifyPayload);
        return new ApiResponse<>(null,true,globalMessageSource.get(OTP_VALIDATED_SUCCESSFULLY));
    }
}
