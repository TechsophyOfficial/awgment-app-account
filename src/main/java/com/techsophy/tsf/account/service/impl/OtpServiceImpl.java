package com.techsophy.tsf.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.MailMessage;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;
import com.techsophy.tsf.account.dto.SMSMessage;
import com.techsophy.tsf.account.entity.OtpDefinition;
import com.techsophy.tsf.account.exception.InvalidDataException;
import com.techsophy.tsf.account.exception.MailException;
import com.techsophy.tsf.account.exception.NoDataFoundException;
import com.techsophy.tsf.account.exception.ServiceNotEnabledException;
import com.techsophy.tsf.account.repository.OtpDefinitionRepository;
import com.techsophy.tsf.account.service.OtpService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import static com.techsophy.tsf.account.constants.AccountConstants.*;
import static com.techsophy.tsf.account.constants.ErrorConstants.*;

@Slf4j
@Service
@AllArgsConstructor(onConstructor_ = {@Autowired})
public class OtpServiceImpl implements OtpService
{
    @Value(NOTIFICATION_SMS_QUERY)
    String smsQuery;
    @Value(NOTIFICATION_EMAIL_QUERY)
    String emailQuery;
    @Value(GATEWAY_URI)
    String gatewayUri;
    @Value(OTP_VALIDITY)
    String validity;

    @Value(OTP_LENGTH)
    Integer otpLength;
    OtpDefinitionRepository otpDefinitionRepository;
    GlobalMessageSource globalMessageSource;
    IdGeneratorImpl idGenerator;
    WebClientWrapper webClientWrapper;
    ObjectMapper objectMapper;
    TokenUtils tokenUtils;
    UserServiceImpl userService;
    Environment environment;
    private static Random random = new Random();

    @Override
    public void generateOtp(OtpRequestPayload otpRequestPayload)
    {
        log.info(GENERATING_OTP);
        if( !Boolean.parseBoolean(environment.getProperty(NOTIFICATION_ENABLED)))
        {
            throw new ServiceNotEnabledException(NOTIFICATION_NOT_ENABLED_ACCOUNTS,globalMessageSource.get(NOTIFICATION_NOT_ENABLED_ACCOUNTS));
        }
        BCryptPasswordEncoder passwordEncoder=new BCryptPasswordEncoder();
        Date expiryTime;
        Calendar calendar=Calendar.getInstance();
        calendar.add(Calendar.SECOND,Integer.parseInt(validity));
        expiryTime=calendar.getTime();
        log.info(FIND_CHANNEL_AND_TO);
        Optional<OtpDefinition> otpDefinition=
                this.otpDefinitionRepository.findByToAndChannel( otpRequestPayload.getTo(),
                        otpRequestPayload.getChannel());
        log.info(OTP_DEFINITION);
        String otp= generateOtpNumber(otpLength);
        String encodedOtp=passwordEncoder.encode(otp);
        Map<String,String> otpData=new HashMap<>();
        otpData.put(OTP,otp);
        log.info(SET_BODY);
        OtpRequestPayload sendMailRequest=otpRequestPayload;
        if(otpRequestPayload.getTemplateData()!=null)
        {
            otpRequestPayload.getTemplateData().put(OTP,otp);
        }
        else
        {
            sendMailRequest=sendMailRequest.withTemplateData(otpData);
        }
        if(otpRequestPayload.getBody()!=null)
        {
            if(otpRequestPayload.getBody().contains(OTP_FILLER))
            {
                sendMailRequest=sendMailRequest.withBody(sendMailRequest.getBody().replace(OTP_FILLER,otp));
            }
            else
            {
                sendMailRequest = sendMailRequest.withBody(otpRequestPayload.getBody().concat(OTP_BODY.concat(otp)));
            }
        }
        else
        {
            sendMailRequest=sendMailRequest.withBody(OTP_BODY.concat(otp));
        }
        log.info(SEND_OTP);
        sendOtpToEmail(sendMailRequest);
        OtpDefinition otpDefinition1;
        if(otpDefinition.isEmpty())
        {
            otpDefinition1 = new OtpDefinition();
            otpDefinition1.setId(idGenerator.nextId());
            otpDefinition1.setChannel(otpRequestPayload.getChannel());
            otpDefinition1.setTo(otpRequestPayload.getTo());
        }
        else
        {
            otpDefinition1 = otpDefinition.get();
        }
        otpDefinition1.setOtp(encodedOtp);
        otpDefinition1.setExpiredAt(expiryTime);
        this.otpDefinitionRepository.save(setCreatedByUserNameAndId(otpDefinition1));
    }

    @Override
    public Boolean verifyOtp(OtpVerifyPayload otpVerifyPayload)
    {
        if(!Boolean.parseBoolean(environment.getProperty(NOTIFICATION_ENABLED)))
        {

            throw new ServiceNotEnabledException(NOTIFICATION_NOT_ENABLED_ACCOUNTS,globalMessageSource.get(NOTIFICATION_NOT_ENABLED_ACCOUNTS));
        }
        OtpDefinition otpDefinition =
                this.otpDefinitionRepository.findByToAndChannel(otpVerifyPayload.getTo(),otpVerifyPayload.getChannel()
                ) .orElseThrow(()-> new NoDataFoundException(INVALID_EMAIL,globalMessageSource.get(INVALID_EMAIL)));
        if(new Date().after(otpDefinition.getExpiredAt()))
        {
            throw new InvalidDataException(OTP_EXPIRED,globalMessageSource.get(OTP_EXPIRED));
        }
        BCryptPasswordEncoder passwordEncoder=new BCryptPasswordEncoder();
        if(otpVerifyPayload.getChannel().equalsIgnoreCase(EMAIL) || otpVerifyPayload.getChannel().equalsIgnoreCase(MOBILE))
        {
            boolean otpMatch =
                    passwordEncoder.matches(String.valueOf(otpVerifyPayload.getOtp()), otpDefinition.getOtp());
            if (!otpMatch)
            {
                throw new InvalidDataException(INCORRECT_OTP,globalMessageSource.get(INCORRECT_OTP));
            }
        }
        return true;
    }

    @SneakyThrows({JsonProcessingException.class})
    private OtpDefinition setCreatedByUserNameAndId(OtpDefinition otpDefinition)
    {
        Map<String,Object> loggedInUser = userService.getCurrentlyLoggedInUserId().get(0);
        otpDefinition.setCreatedOn(Instant.now());
        otpDefinition.setCreatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        otpDefinition.setUpdatedOn(Instant.now());
        otpDefinition.setUpdatedById(BigInteger.valueOf(Long.parseLong(loggedInUser.get(ID).toString())));
        return otpDefinition;
    }

    @SneakyThrows({JsonProcessingException.class})
    public void sendOtpToEmail(OtpRequestPayload otpRequestPayload)
    {
        Map<String,Object> emailMessage =new HashMap<>();
        Map<String,Object> emailData =new HashMap<>();
        WebClient webClient=webClientWrapper.createWebClient(tokenUtils.getTokenFromContext());
        if(otpRequestPayload.getChannel().equalsIgnoreCase(EMAIL))
        {
            MailMessage message=objectMapper.convertValue(otpRequestPayload,MailMessage.class).withData(otpRequestPayload.getTemplateData());
            emailData.put(MAIL_MESSAGE,objectMapper.convertValue(message,Map.class));
            emailMessage.put(OPERATION_NAME,SEND_EMAIL);
            emailMessage.put(VARIABLES_STRING,emailData);
            emailMessage.put(QUERY_STRING,emailQuery);
        }
        else if(otpRequestPayload.getChannel().equalsIgnoreCase(MOBILE))
        {
            SMSMessage message=
                    objectMapper.convertValue(otpRequestPayload, SMSMessage.class).withData(otpRequestPayload.getTemplateData());
            emailData.put(SMS_MESSAGE,objectMapper.convertValue(message,Map.class));
            emailMessage.put(OPERATION_NAME,SEND_SMS);
            emailMessage.put(VARIABLES_STRING,emailData);
            emailMessage.put(QUERY_STRING,smsQuery);
        }
        log.info(INVOKING_EMAIL_SERVICE);
        String sendEmailResponse =
                webClientWrapper.webclientRequest(webClient, gatewayUri + NOTIFICATION_URL, POST, emailMessage);
        var responseData=objectMapper.readValue(sendEmailResponse,Map.class);
        if(responseData.containsKey(ERRORS))
        {
            throw new MailException(UNABLE_TO_SEND_EMAIL,globalMessageSource.get(UNABLE_TO_SEND_EMAIL,otpRequestPayload.getChannel()));
        }
    }

    static String generateOtpNumber(int len)
    {
        // Using numeric values
        String numbers = NUMBERS;
        // Using random method
        char[] otp = new char[len];
        for (int i = 0; i < len; i++)
        {
            otp[i] =
                    numbers.charAt(random.nextInt(numbers.length()));
        }
        return String.valueOf(otp);
    }
}
