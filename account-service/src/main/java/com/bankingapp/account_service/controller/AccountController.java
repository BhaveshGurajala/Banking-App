package com.bankingapp.account_service.controller;

import com.bankingapp.account_service.dto.AccountDto;
import com.bankingapp.account_service.dto.BalanceUpdateRequest;
import com.bankingapp.account_service.dto.CreateAccountRequest;
import com.bankingapp.account_service.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountDto> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.createAccount(request));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountDto> getAccount(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getByAccountNumber(accountNumber));
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<List<AccountDto>> getAccountsForUser(@PathVariable String username) {
        return ResponseEntity.ok(accountService.getAccountsForUser(username));
    }

    @PutMapping("/{accountNumber}/debit")
    public ResponseEntity<AccountDto> debit(@PathVariable String accountNumber,
                                            @Valid @RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(accountService.debit(accountNumber, request.getAmount()));
    }

    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<AccountDto> credit(@PathVariable String accountNumber,
                                             @Valid @RequestBody BalanceUpdateRequest request) {
        return ResponseEntity.ok(accountService.credit(accountNumber, request.getAmount()));
    }
}