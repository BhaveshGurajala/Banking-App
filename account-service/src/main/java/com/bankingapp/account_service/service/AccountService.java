package com.bankingapp.account_service.service;

import com.bankingapp.account_service.dto.AccountDto;
import com.bankingapp.account_service.dto.CreateAccountRequest;
import com.bankingapp.account_service.entity.Account;
import com.bankingapp.account_service.exception.AccountNotFoundException;
import com.bankingapp.account_service.exception.InsufficientBalanceException;
import com.bankingapp.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountDto createAccount(CreateAccountRequest request) {
        Account account = Account.builder()
                .ownerUsername(request.getOwnerUsername())
                .accountType(request.getAccountType())
                .balance(request.getInitialDeposit())
                .build();

        return toDto(accountRepository.save(account));
    }

    public AccountDto getByAccountNumber(String accountNumber) {
        return toDto(findAccountOrThrow(accountNumber));
    }

    public List<AccountDto> getAccountsForUser(String username) {
        return accountRepository.findByOwnerUsername(username)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public AccountDto debit(String accountNumber, BigDecimal amount) {
        Account account = findAccountOrThrow(accountNumber);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException(
                    "Insufficient balance in account " + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        return toDto(accountRepository.save(account));
    }

    @Transactional
    public AccountDto credit(String accountNumber, BigDecimal amount) {
        Account account = findAccountOrThrow(accountNumber);
        account.setBalance(account.getBalance().add(amount));
        return toDto(accountRepository.save(account));
    }

    private Account findAccountOrThrow(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Account not found: " + accountNumber));
    }

    private AccountDto toDto(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .ownerUsername(account.getOwnerUsername())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .status(account.getStatus())
                .build();
    }
}
