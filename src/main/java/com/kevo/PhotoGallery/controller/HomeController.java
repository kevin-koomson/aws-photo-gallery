package com.kevo.PhotoGallery.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@SessionAttributes("userName")
public class HomeController {
    @GetMapping("/")
    public String index(Model model, @SessionAttribute(value = "userName", required = false) String userName) {
        if (userName != null && !userName.isEmpty()) {
            model.addAttribute("userName", userName);
            return "dashboard";
        }
        return "index";
    }

    @PostMapping("/welcome")
    public String welcome(@RequestParam("name") String name, Model model) {
        if (name == null || name.trim().isEmpty()) {
            model.addAttribute("error", "Please enter your name");
            return "index";
        }
        model.addAttribute("userName", name.trim());
        return "dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@SessionAttribute(value = "userName", required = false) String userName, Model model) {
        if (userName == null || userName.isEmpty()) {
            return "redirect:/";
        }
        model.addAttribute("userName", userName);
        return "dashboard";
    }
}