package codechecker.rest.mvc;

import codechecker.rest.resources.*;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import codechecker.core.models.entities.Account;
import codechecker.core.models.entities.Assignment;
import codechecker.core.services.AccountService;
import codechecker.core.services.exceptions.AccountDoesNotExistException;
import codechecker.core.services.exceptions.AccountExistsException;
import codechecker.core.services.exceptions.AssignmentExistsException;
import codechecker.core.services.util.AccountList;
import codechecker.core.services.util.AssignmentList;
import codechecker.rest.exceptions.ConflictException;
import codechecker.rest.exceptions.ForbiddenException;
import codechecker.rest.exceptions.NotFoundException;
import codechecker.rest.resources.asm.AccountListResourceAsm;
import codechecker.rest.resources.asm.AccountResourceAsm;
import codechecker.rest.resources.asm.AssignmentListResourceAsm;
import codechecker.rest.resources.asm.AssignmentResourceAsm;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Account Controller class
 */
@Controller
@RequestMapping("/rest/accounts")
public class AccountController {
    private AccountService accountService;

    /**
     * Account Service controller
     */
    @Autowired
    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Function to find all accounts in account service
     */
    @RequestMapping(method = RequestMethod.GET)
    @PreAuthorize("permitAll")
    public ResponseEntity<AccountListResource> findAllAccounts(@RequestParam(value="name", required = false) String name, @RequestParam(value="password", required = false) String password) {
        AccountList list = null;
        if(name == null) {
            list = accountService.findAllAccounts();
        } else {
            Account account = accountService.findByAccountName(name);
            list = new AccountList(new ArrayList<Account>());
            if(account != null) {
                if(password != null) {
                    if(account.getPassword().equals(password)) {
                        list = new AccountList(Arrays.asList(account));
                    }
                } else {
                    list = new AccountList(Arrays.asList(account));
                }
            }
        }
        AccountListResource res = new AccountListResourceAsm().toResource(list);
        return new ResponseEntity<AccountListResource>(res, HttpStatus.OK);
    }

    /**
     * Function to create new accounts in account service
     */
    @RequestMapping(method = RequestMethod.POST)
    @PreAuthorize("permitAll")
    public ResponseEntity<AccountResource> createAccount(
            @RequestBody AccountResource sentAccount) {
        try {
            Account createdAccount = accountService.createAccount(sentAccount.toAccount());
            AccountResource res = new AccountResourceAsm().toResource(createdAccount);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(res.getLink("self").getHref()));
            return new ResponseEntity<AccountResource>(res, headers, HttpStatus.CREATED);
        } catch(AccountExistsException exception) {
            throw new ConflictException(exception);
        }
    }

    /**
     * Function to get account id in account service
     */
    @RequestMapping(value="/getMeId", method = RequestMethod.GET)
    @PreAuthorize("permitAll")
    public ResponseEntity<AccountResource> getMeId(Principal principal) {
        Account account = accountService.findByAccountName(principal.getName());
        if(account != null) {
            AccountResource res = new AccountResourceAsm().toResource(account);
            return new ResponseEntity<AccountResource>(res, HttpStatus.OK);
        } else {
            return new ResponseEntity<AccountResource>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Function to find accounts in account service
     */
    @RequestMapping( value="/{accountId}",
                method = RequestMethod.GET)
    @PreAuthorize("permitAll")
    public ResponseEntity<AccountResource> getAccount(
            @PathVariable Long accountId
    ) {
        Account account = accountService.findAccount(accountId);
        if(account != null)
        {
            AccountResource res = new AccountResourceAsm().toResource(account);
            return new ResponseEntity<AccountResource>(res, HttpStatus.OK);
        } else {
            return new ResponseEntity<AccountResource>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Function to find account by username in account service
     */
    @RequestMapping( value="/check-username/{username}",
            method = RequestMethod.GET)
    @PreAuthorize("permitAll")
    public ResponseEntity<AccountResource> findAccountByUsername(
            @PathVariable String username
    ) {
        Account account = accountService.findByAccountName(username);
        if(account != null) {
            AccountResource res = new AccountResourceAsm().toResource(account);
            return new ResponseEntity<AccountResource>(res, HttpStatus.OK);
        } else {
            return new ResponseEntity<AccountResource>(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Function to create assignments in account service
     */
    @RequestMapping(value="/{accountId}/assignments",
            method = RequestMethod.POST)
    @PreAuthorize("permitAll")
    public ResponseEntity<AssignmentResource> createAssignment(
            @RequestBody AssignmentResource res,
            @PathVariable Long accountId) {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal instanceof UserDetails) {
            UserDetails details = (UserDetails)principal;
            Account loggedIn = accountService.findByAccountName(details.getUsername());
            if(loggedIn.getId() == accountId) {
                try {
                    Assignment createdAssignment = accountService.createAssignment(accountId, res.toAssignment());
                    AssignmentResource createdAssignmentRes = new AssignmentResourceAsm().toResource(createdAssignment);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setLocation(URI.create(createdAssignmentRes.getLink("self").getHref()));
                    return new ResponseEntity<AssignmentResource>(createdAssignmentRes, headers, HttpStatus.CREATED);
                } catch(AccountDoesNotExistException exception) {
                    throw new NotFoundException(exception);
                } catch(AssignmentExistsException exception) {
                    throw new ConflictException(exception);
                }
            } else {
                throw new ForbiddenException();
            }
        } else {
            throw new ForbiddenException();
        }
    }


    /**
     * Function to find all assignments in account service
     */
    @RequestMapping(value="/{accountId}/assignments",
            method = RequestMethod.GET)
    @PreAuthorize("permitAll")
    public ResponseEntity<AssignmentListResource> findAllAssignments(
            @PathVariable Long accountId) {
        try {
            AssignmentList assignmentList = accountService.findAssignmentsByAccount(accountId);
            AssignmentListResource assignmentListRes = new AssignmentListResourceAsm().toResource(assignmentList);
            return new ResponseEntity<AssignmentListResource>(assignmentListRes, HttpStatus.OK);
        } catch(AccountDoesNotExistException exception) {
            throw new NotFoundException(exception);
        }
    }



}
