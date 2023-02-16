package com.techsophy.tsf.account.utils;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.exception.*;
import com.techsophy.tsf.account.model.ApiErrorResponse;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import javax.validation.ConstraintViolationException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest
{
  @Mock
  GlobalMessageSource globalMessageSource;
  @Mock
  WebRequest webRequest;
  @Mock
  Exception exception;
  @Mock
  DataAccessException dataAccessException;
  @InjectMocks
  GlobalExceptionHandler globalExceptionHandler;

  @Test
  void handleCreateFormDataException()
  {
      CreateFormDataException createFormDataException = new CreateFormDataException("error","args");
      ResponseEntity<ApiErrorResponse> response =globalExceptionHandler.handleCreateFormDataException(createFormDataException,webRequest);
      Assertions.assertNotNull(response);
  }

  @Test
  void CreateTenantException()
  {
    CreateTenantException createTenantException = new CreateTenantException("error","args");
    ResponseEntity<ApiErrorResponse> response =globalExceptionHandler.handleCreateTenantException(createTenantException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleDomainException()
  {
    DomainException domainException = new DomainException("error","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleDomainException(domainException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleInvalidInputException()
  {
    InvalidInputException invalidInputException = new InvalidInputException("error","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleInvalidInputException(invalidInputException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleUserException()
  {
    UserNotFoundException userNotFoundException = new UserNotFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleUserException(userNotFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void entityNotFoundException()
  {
    EntityNotFoundByIdException entityNotFoundByIdException = new EntityNotFoundByIdException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.entityNotFoundException(entityNotFoundByIdException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void methodArgumentNotValid()
  {
    ConstraintViolationException constraintViolationException = new ConstraintViolationException(null);
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.methodArgumentNotValid(constraintViolationException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void methodArgumentNotValidTest()
  {
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.methodArgumentNotValid(dataAccessException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleFormException()
  {
    UserFormDataNotFoundException userFormDataNotFoundException = new UserFormDataNotFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response =globalExceptionHandler.handleFormException(userFormDataNotFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleRolesException()
  {
    RolesNotFoundException userFormDataNotFoundException = new RolesNotFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleRolesException(userFormDataNotFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleRealmException()
  {
    RealmAcessException realmAcessException = new RealmAcessException("args","args");
    ResponseEntity<ApiErrorResponse> response =  globalExceptionHandler.handleRealmException(realmAcessException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleRealmExceptionTest()
  {
    MailException mailException = new MailException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleRealmException(mailException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleGroupsException()
  {
    GroupsNotFoundException groupsNotFoundException = new GroupsNotFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleGroupsException(groupsNotFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleProfilePictureNotPresentException1Test()
  {
    NoDataFoundException noDataFoundException = new NoDataFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(noDataFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException2Test()
  {
    BulkUserNotFoundException bulkUserNotFoundException = new BulkUserNotFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(bulkUserNotFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException3Test()
  {
    InvalidDataException invalidDataException = new InvalidDataException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(invalidDataException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException4Test()
  {
    InvalidProfilePictureException invalidProfilePictureException = new InvalidProfilePictureException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(invalidProfilePictureException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException5Test()
  {
    ServiceNotEnabledException serviceNotEnabledException= new ServiceNotEnabledException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(serviceNotEnabledException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException6Test()
  {
    ThemesNotFoundByIdException themesNotFoundByIdException = new ThemesNotFoundByIdException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(themesNotFoundByIdException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException7Test()
  {
    UserDetailsIdNotFoundException userDetailsIdNotFoundException = new UserDetailsIdNotFoundException("args","args");
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(userDetailsIdNotFoundException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void HandleProfilePictureNotPresentException8Test()
  {
    long actual = 123456;
    long permitted =980765;
    FileSizeLimitExceededException fileSizeLimitExceededException = new FileSizeLimitExceededException("args",actual,permitted);
    ResponseEntity<ApiErrorResponse> response = globalExceptionHandler.handleProfilePictureNotPresentException(fileSizeLimitExceededException,webRequest);
    Assertions.assertNotNull(response);
  }

  @Test
  void handleUserNameExceptionTest()
  {
    UserNameValidationException userNameValidationException=new UserNameValidationException("errorCode","userName should not start with service-account");
   Assertions.assertNotNull(globalExceptionHandler.handleUserName(userNameValidationException,webRequest));
  }

  @Test
  void handleEntityIdNotFoundExceptionTest()
  {
    EntityIdNotFoundException entityIdNotFoundException=new EntityIdNotFoundException("errorCode","userName should not start with service-account");
    Assertions.assertNotNull(globalExceptionHandler.handleEntityException(entityIdNotFoundException,webRequest));
  }
}
