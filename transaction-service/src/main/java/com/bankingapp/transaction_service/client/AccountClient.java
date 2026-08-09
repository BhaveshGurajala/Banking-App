package com.bankingapp.transaction_service.client;

import com.bankingapp.transaction_service.dto.AccountDto;
import com.bankingapp.transaction_service.dto.BalanceUpdateRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name="ACCOUNT-SERVICE")
public interface AccountClient {

    @GetMapping("/api/accounts/{accountNumber}")
    AccountDto getAccount(@PathVariable String accountNumber);

    @PutMapping("/api/accounts/{accountNumber}/debit")
    AccountDto debit(@PathVariable String accountNumber, @RequestBody BalanceUpdateRequest request);

    @PutMapping("/api/accounts/{accountNumber}/credit")
    AccountDto credit(@PathVariable String accountNumber, @RequestBody BalanceUpdateRequest request);

}
