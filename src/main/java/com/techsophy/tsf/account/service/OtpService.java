package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;

public interface OtpService
{
    void generateOtp(OtpRequestPayload otpRequestPayload) throws JsonProcessingException;

    Boolean verifyOtp(OtpVerifyPayload otpVerifyPayload);
}
