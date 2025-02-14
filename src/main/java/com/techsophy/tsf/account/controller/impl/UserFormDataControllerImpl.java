package com.techsophy.tsf.account.controller.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.controller.UserFormDataController;
import com.techsophy.tsf.account.dto.AuditableData;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.entity.UserFormDataDefinition;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.model.ApiResponse;
import com.techsophy.tsf.account.service.UserFormDataService;
import com.techsophy.tsf.account.utils.TokenUtils;
import com.techsophy.tsf.account.utils.UserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

/**
 * Implementation of {@link UserFormDataController} responsible for handling user form data operations.
 */
@RestController
@RequiredArgsConstructor
public class UserFormDataControllerImpl implements UserFormDataController {
    private final UserFormDataService userFormDataService;
    private final GlobalMessageSource globalMessageSource;
    private final TokenUtils tokenUtils;
    private final UserDetails userDetails;
    private final ObjectMapper objectMapper;

    /**
     * Retrieves the details of the currently logged-in user.
     *
     * @return ApiResponse containing the user details.
     */
    @Override
    public ApiResponse<UserFormDataSchema> getUserDetailsOfLoggedInUser() {
        Optional<BigInteger> userId = userDetails.getUserId();
        if (userId.isEmpty()) {
            String loginId = tokenUtils.getLoggedInUserId();
            UserFormDataDefinition userFormDataDefinition = userFormDataService.getUserFormData(loginId);
            UserFormDataSchema userFormDataSchema = objectMapper.convertValue(userFormDataDefinition, UserFormDataSchema.class);
            return new ApiResponse<>(userFormDataSchema, true, "Logged In User details fetched successfully");
        } else {
            AuditableData auditableData = userFormDataService.getUserFormDataByUserId(String.valueOf(userId.get()), false);
            UserFormDataSchema userFormDataSchema = objectMapper.convertValue(auditableData, UserFormDataSchema.class);
            return new ApiResponse<>(userFormDataSchema, true, "Logged In User details fetched successfully");
        }
    }

    /**
     * Updates the details of the currently logged-in user.
     *
     * @param userFormDataSchema The updated user form data.
     * @return ApiResponse containing the updated user details.
     */
    @Override
    public ApiResponse<UserFormDataSchema> updateUserDetailsOfLoggedInUser(UserFormDataSchema userFormDataSchema) {
        try {
            String userId = (String) userDetails.getUserDetails().get(0).get(ID);
            AuditableData auditableData = userFormDataService.getUserFormDataByUserId(userId, false);
            UserFormDataSchema existingFormData = this.objectMapper.convertValue(auditableData, UserFormDataSchema.class);
            Map<String, Object> userData = userFormDataSchema.getUserData();
            userData.put("userName", existingFormData.getUserData().get("userName"));
            userData.put("groups", existingFormData.getUserData().get("groups"));
            userData.put("roles", existingFormData.getUserData().get("roles"));
            userData.put("emailId", existingFormData.getUserData().get("emailId"));
            userFormDataSchema.setUserData(userData);
            if (userFormDataSchema.getUserId() != null && userFormDataSchema.getUserId().equalsIgnoreCase(userId)) {
                return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema), true, UPDATED_SUCCESSFULLY);
            } else if (userFormDataSchema.getUserId() == null) {
                userFormDataSchema.setUserId(userId);
                return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema), true, UPDATED_SUCCESSFULLY);
            } else {
                throw new BadRequestException(NOT_LOGGIN_USER_ID, globalMessageSource.get(NOT_LOGGIN_USER_ID));
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new RunTimeException(e.getMessage());
        }
    }

    /**
     * Saves user form data.
     *
     * @param userFormDataSchema The user form data to be saved.
     * @param headers            The HTTP headers.
     * @return ApiResponse containing the saved user form data.
     */
    @Override
    public ApiResponse<UserFormDataSchema> saveUser(UserFormDataSchema userFormDataSchema, HttpHeaders headers) {
        return new ApiResponse<>(userFormDataService.saveUserFormData(userFormDataSchema), true, globalMessageSource.get(SAVE_FORM_SUCCESS));
    }

    /**
     * Retrieves user data by user ID.
     *
     * @param userId             The ID of the user.
     * @param onlyMandatoryFields Whether to retrieve only mandatory fields.
     * @return ApiResponse containing the user form data.
     */
    @Override
    public ApiResponse<AuditableData> getUserByUserId(String userId, Boolean onlyMandatoryFields) {
        return new ApiResponse<>(userFormDataService.getUserFormDataByUserId(userId, onlyMandatoryFields), true, globalMessageSource.get(GET_FORM_SUCCESS));
    }

    /**
     * Retrieves all users with optional filtering, pagination, and sorting.
     *
     * @param q                  The search query.
     * @param onlyMandatoryFields Whether to retrieve only mandatory fields.
     * @param page               The page number for pagination.
     * @param pageSize           The number of records per page.
     * @param sortBy             The sorting criteria.
     * @param filterColumn       The filter column.
     * @param filterValue        The filter value.
     * @return ApiResponse containing the list of users.
     */
    @Override
    public ApiResponse getAllUsers(String q, Boolean onlyMandatoryFields, Integer page, Integer pageSize, String[] sortBy, String filterColumn, String filterValue) {
        if (StringUtils.hasText(filterColumn) && StringUtils.hasText(filterValue)) {
            if (page == null) {
                return new ApiResponse<>(userFormDataService.getAllUsersByFilter(onlyMandatoryFields, filterColumn, filterValue, tokenUtils.getSortBy(sortBy), q), true,
                        globalMessageSource.get(GET_FORM_SUCCESS));
            } else {
                return new ApiResponse<>(userFormDataService.getAllUsersByFilter(onlyMandatoryFields, filterColumn, filterValue, tokenUtils.getPageRequest(page, pageSize, sortBy), q), true,
                        globalMessageSource.get(GET_FORM_SUCCESS));
            }
        }
        if (page == null) {
            return new ApiResponse<>(userFormDataService.getAllUserFormDataObjects(onlyMandatoryFields, q, tokenUtils.getSortBy(sortBy)), true,
                    globalMessageSource.get(GET_FORM_SUCCESS));
        }
        return new ApiResponse<>(userFormDataService.getAllUserFormDataObjects(onlyMandatoryFields, q, tokenUtils.getPageRequest(page, pageSize, sortBy)), true,
                globalMessageSource.get(GET_FORM_SUCCESS));
    }

    /**
     * Deletes a user based on the provided user ID.
     *
     * @param userId The ID of the user to be deleted.
     * @return ApiResponse indicating the success of the deletion.
     */
    @Override
    public ApiResponse<Void> deleteUserByUserId(String userId) {
        userFormDataService.deleteUserFormDataByUserId(userId);
        return new ApiResponse<>(null, true, globalMessageSource.get(DELETE_USER_SUCCESS));
    }

    /**
     * Fetches registered users within a specified date range.
     *
     * @param startDate The start date of the range.
     * @param endDate   The end date of the range.
     * @return ApiResponse containing the list of users registered within the date range.
     */
    @Override
    public ApiResponse<List<UserFormDataDefinition>> fetchRegisteredUsersByDateRange(String startDate, String endDate) {
        List<UserFormDataDefinition> usersData = userFormDataService.getUsersRegisteredByDateRange(startDate, endDate);
        return new ApiResponse<>(usersData, true, FETCH_USERS_REGISTERED_BY_DATE_RANGE_MSG);
    }
}
