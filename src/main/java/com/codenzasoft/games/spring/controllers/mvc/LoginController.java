package com.codenzasoft.games.spring.controllers.mvc;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * {@link LoginController} is used to generate a login page with its corresponding Thymeleaf
 * template found in the <code>resources/templates</code> directory.
 */
@Controller
class LoginController {
  @GetMapping("/login")
  String login(Model model) {
    model.addAttribute(PageConstants.PAGE_TITLE, "Sign in | Codenza");
    return "login";
  }
}
