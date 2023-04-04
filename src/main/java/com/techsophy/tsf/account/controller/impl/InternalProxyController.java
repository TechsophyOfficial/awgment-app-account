package com.techsophy.tsf.account.controller.impl;

import com.techsophy.tsf.account.dto.MenuResponseSchema;
import com.techsophy.tsf.account.model.ApiResponse;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;


@RestController
public class InternalProxyController implements com.techsophy.tsf.account.controller.InternalProxyController {

    public ApiResponse<MenuResponseSchema> createMenu() throws IOException {
        return new ApiResponse<>(new MenuResponseSchema(
                "1"),
                false,
                "failed"
        );
    }
}
