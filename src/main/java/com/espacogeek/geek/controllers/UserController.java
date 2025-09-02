package com.espacogeek.geek.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import com.espacogeek.geek.exception.GenericException;
import com.espacogeek.geek.models.UserModel;
import com.espacogeek.geek.services.UserService;
import com.espacogeek.geek.types.NewUser;
import com.espacogeek.geek.utils.Utils;

import at.favre.lib.crypto.bcrypt.BCrypt;

@Controller
public class UserController {
    @Autowired
    private UserService userService;

    @QueryMapping
    public List<UserModel> findUser(@Argument Integer id, @Argument String username, @Argument String email) {
        return userService.findByIdOrUsernameContainsOrEmail(id, username, email);
    }

    @QueryMapping(name = "login")
    @PreAuthorize("hasRole('user')")
    public String doLoginUser() {
        return HttpStatus.ACCEPTED.toString();
    }


    @MutationMapping(name = "createUser")
    public String createUser(@Argument(name = "credentials") NewUser newUser) {

        if (!Utils.isValidPassword(newUser.password())) {
            throw new GenericException(HttpStatus.BAD_REQUEST.toString());
        }

        if (newUser.username().trim().length() < 3 || newUser.username().trim().length() > 21) {
            throw new GenericException(HttpStatus.BAD_REQUEST.toString());
        }

        var passwordCrypt = BCrypt.withDefaults().hash(12, newUser.password().toCharArray());
        var user = new UserModel(null, newUser.username().trim(), newUser.email().toLowerCase().trim(), passwordCrypt, null);

        userService.save(user);

        return HttpStatus.CREATED.toString();
    }

    @MutationMapping(name = "editPassword")
    @PreAuthorize("hasRole('user')")
    public String editPasswordUserLogged(Authentication authentication, @Argument String actualPassword, @Argument String newPassword) {

        if (!Utils.isValidPassword(newPassword)) {
            throw new GenericException(HttpStatus.BAD_REQUEST.toString());
        }

        var userId = Utils.getUserID(authentication);

        var userLogged = userService.findById(Integer.valueOf(userId)).get();
        var resultPassword = BCrypt.verifyer().verify(actualPassword.toCharArray(), userLogged.getPassword()).verified;

        if (resultPassword) {
            userLogged.setPassword(BCrypt.withDefaults().hash(12, newPassword.toCharArray()));
            userService.save(userLogged);
            return HttpStatus.OK.toString();
        }

        throw new GenericException(HttpStatus.UNAUTHORIZED.toString());
    }

    @MutationMapping(name = "deleteUser")
    @PreAuthorize("hasRole('user')")
    public String deleteUserLogged(Authentication authentication, @Argument String password) {

        var userId = Utils.getUserID(authentication);

        var userLogged = userService.findById(userId).get();
        var resultPassword = BCrypt.verifyer().verify(password.toCharArray(), userLogged.getPassword()).verified;

        if (resultPassword) {
            userService.deleteById(Integer.valueOf(userId));
            return HttpStatus.OK.toString();
        }

        throw new GenericException(HttpStatus.UNAUTHORIZED.toString());
    }

    @MutationMapping(name = "editUsername")
    @PreAuthorize("hasRole('user')")
    public String editUsernameUserLogged(Authentication authentication, @Argument String password, @Argument String newUsername) {

        var userId = Utils.getUserID(authentication);

        var userLogged = userService.findById(userId).get();
        var resultPassword = BCrypt.verifyer().verify(password.toCharArray(), userLogged.getPassword()).verified;

        if (resultPassword) {
            userLogged.setUsername(newUsername);
            userService.save(userLogged);
            return HttpStatus.OK.toString();
        }

        throw new GenericException(HttpStatus.UNAUTHORIZED.toString());
    }

    @MutationMapping(name = "editEmail")
    @PreAuthorize("hasRole('user')")
    public String editEmailUserLogged(Authentication authentication, @Argument String password, @Argument String newEmail) {

        var userId = Utils.getUserID(authentication);

        var userLogged = userService.findById(userId).get();
        var resultPassword = BCrypt.verifyer().verify(password.toCharArray(), userLogged.getPassword()).verified;

        if (resultPassword) {
            userLogged.setEmail(newEmail);
            userService.save(userLogged);
            return HttpStatus.OK.toString();
        }

        throw new GenericException(HttpStatus.UNAUTHORIZED.toString());
    }
}
