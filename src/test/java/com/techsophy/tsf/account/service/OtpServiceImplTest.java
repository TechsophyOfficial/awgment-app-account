package com.techsophy.tsf.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.idgenerator.IdGeneratorImpl;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.MailMessage;
import com.techsophy.tsf.account.dto.OtpRequestPayload;
import com.techsophy.tsf.account.dto.OtpVerifyPayload;
import com.techsophy.tsf.account.dto.SMSMessage;
import com.techsophy.tsf.account.entity.OtpDefinition;
import com.techsophy.tsf.account.exception.InvalidDataException;
import com.techsophy.tsf.account.repository.OtpDefinitionRepository;
import com.techsophy.tsf.account.service.impl.OtpServiceImpl;
import com.techsophy.tsf.account.service.impl.UserServiceImpl;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.WebClientWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import java.math.BigInteger;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class})
 class OtpServiceImplTest
{
    @Mock
    WebClientWrapper webClientWrapper;
    @Mock
    TokenUtils mockTokenUtils;
    @Mock
    ObjectMapper mockObjectMapper;
    @Mock
    UserServiceImpl mockUserServiceImpl;
    @InjectMocks
    OtpServiceImpl otpService;
    @Mock
    Environment environment;
    @Mock
    OtpDefinitionRepository otpDefinitionRepository;
    @Mock
    BCryptPasswordEncoder passwordEncoder;
    @Mock
    GlobalMessageSource globalMessageSource;
    @Mock
    IdGeneratorImpl idGenerator;
    List<Map<String,String>> list = new ArrayList<>();
    Map<String,String> map = new HashMap<>();
    @BeforeEach
    public void init()
    {
        list = new ArrayList<>();
        map = new HashMap<>();
        map.put("firstname","nandini");
        map.put("id","1");
        map.put("lastname","nandini");
        list.add(map);
        ReflectionTestUtils.setField(otpService, "smsQuery", "${notification.sms.query}");
        ReflectionTestUtils.setField(otpService, "emailQuery", "${notification.email.query}");
        ReflectionTestUtils.setField(otpService, "gatewayUri", "${gateway.uri}");
        ReflectionTestUtils.setField(otpService, "validity", "120");
        ReflectionTestUtils.setField(otpService, "otpLength", 6);
    }
    @Test
    void generateOtpTest() throws Exception
    {
        MailMessage mailMessage = new MailMessage("1",map,"abc","abc","abc","abc","abc");
        Date date = new Date(2011, 5, 21);
        List<Map<String,Object>> list1 = new ArrayList<>();
        Map<String,Object> map1 = new HashMap<>();
        Map<String,String> map3 = new HashMap<>();
        map1.put("firstName","Ganga");
        map1.put("secondName","nandini");
        map1.put("id","1");
        map3.put("id","1");
        SMSMessage smsMessage = new SMSMessage("1",map3,"abc","abc","abc");
        list1.add(map1);
        List<Map<String,Object>> list = new ArrayList<>();
        Map<String,Object> map2 = new HashMap<>();
        map2.put("firstName","Ganga");
        map2.put("secondName","nandini");
        map2.put("id","1");
        list.add(map2);
        ObjectMapper objectMapper = new ObjectMapper();
        OtpRequestPayload otpRequestPayload = new OtpRequestPayload("email","abc","abc","abc","abc","abc","1", map);
        OtpRequestPayload otpRequestPayload1 = new OtpRequestPayload("mobile","abc","abc","abc","abc","abc","1", map);
        when(environment.getProperty(anyString())).thenReturn(String.valueOf(true));
        OtpDefinition otpDefinition = new OtpDefinition(BigInteger.ONE,"email","abc",null, date);
        String json = objectMapper.writeValueAsString(list);
        WebClient webClient= WebClient.builder().build();
        when(webClientWrapper.createWebClient(any())).thenReturn(webClient);
        when(otpDefinitionRepository.findByToAndChannel(anyString(),anyString())).thenReturn(Optional.of(otpDefinition)).thenReturn(Optional.empty());
        when(otpDefinitionRepository.save(any())).thenReturn(otpDefinition);
        when(idGenerator.nextId()).thenReturn(BigInteger.valueOf(1));
        Mockito.when(webClientWrapper.webclientRequest(any(WebClient.class),anyString(),anyString(), anyMap())).thenReturn(json);
        when(mockTokenUtils.getTokenFromContext()).thenReturn("abc");
        when(mockObjectMapper.convertValue(any(),eq(MailMessage.class))).thenReturn(mailMessage);
        when(mockObjectMapper.convertValue(any(),eq(SMSMessage.class))).thenReturn(smsMessage);
        when(mockObjectMapper.readValue(anyString(),eq(Map.class))).thenReturn(map1).thenReturn(map2);
        when(mockUserServiceImpl.getCurrentlyLoggedInUserId()).thenReturn(list1);
        otpService.generateOtp(otpRequestPayload);
        otpService.generateOtp(otpRequestPayload1);
        Mockito.verify(otpDefinitionRepository,Mockito.times(2)).findByToAndChannel(anyString(),anyString());
    }
    @Test
    void verifyOtpTest()
    {
        Date date = new Date(2011, 5, 21);
        Date date2 = new Date(2015, 1, 21);
        OtpDefinition otpDefinition = new OtpDefinition(BigInteger.ONE,"abc","abc",null, date);
        OtpVerifyPayload otpVerifyPayload = new OtpVerifyPayload("email","abc",null);
        when(environment.getProperty(anyString())).thenReturn("true");
        when(passwordEncoder.matches(anyString(),anyString())).thenReturn(true);
        when(globalMessageSource.get(anyString())).thenReturn("abc");
        when(otpDefinitionRepository.findByToAndChannel(anyString(),anyString())).thenReturn(Optional.of(otpDefinition));
        Assertions.assertThrows(InvalidDataException.class,()->otpService.verifyOtp(otpVerifyPayload));
    }
}
