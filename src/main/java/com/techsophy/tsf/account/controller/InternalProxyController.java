package com.techsophy.tsf.account.controller;


import com.techsophy.tsf.account.dto.MenuResponseSchema;
import com.techsophy.tsf.account.dto.MenuSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;

import static com.techsophy.tsf.account.constants.AccountConstants.*;

@RequestMapping("/internal/v1/")

public interface InternalProxyController {
    @PostMapping("users")
    ApiResponse<MenuResponseSchema> createMenu() throws IOException;


}
