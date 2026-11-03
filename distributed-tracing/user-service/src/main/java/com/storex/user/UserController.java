package com.storex.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/users/tracing-test")
    public UserInformation tracingTest() {
        logger.info("Received user information request");
        logger.debug("Looking up tracing demo user");
        return userService.findTracingTestUser();
    }
}
