package com.bankingapp.transaction_service.service;

import com.bankingapp.transaction_service.client.AccountClient;
import com.bankingapp.transaction_service.dto.AmountRequest;
import com.bankingapp.transaction_service.dto.BalanceUpdateRequest;
import com.bankingapp.transaction_service.dto.TransferRequest;
import com.bankingapp.transaction_service.entity.Transaction;
import com.bankingapp.transaction_service.repository.TransactionRepository;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountClient accountClient;

    @CircuitBreaker(name="accountService", fallbackMethod = "depositFallback")
    public Transaction deposit(AmountRequest request) {
        accountClient.credit(request.getAccountNumber(), new BalanceUpdateRequest(request.getAmount()));

        Transaction txn = Transaction.builder()
                .type("DEPOSIT")
                .toAccountNumber(request.getAccountNumber())
                .amount(request.getAmount())
                .status("SUCCESS")
                .build();
        return transactionRepository.save(txn);
    }

    @CircuitBreaker(name="accountService", fallbackMethod = "withdrawFallback")
    public Transaction withdraw(AmountRequest request) {
        accountClient.debit(request.getAccountNumber(), new BalanceUpdateRequest(request.getAmount()));

        Transaction txn = Transaction.builder()
                .type("WITHDRAWAL")
                .fromAccountNumber(request.getAccountNumber())
                .amount(request.getAmount())
                .status("SUCCESS")
                .build();
        return transactionRepository.save(txn);
    }

    @CircuitBreaker(name="accountService", fallbackMethod = "transferFallback")
    public Transaction transfer(TransferRequest request) {

        accountClient.debit(request.getFromAccountNumber(), new BalanceUpdateRequest(request.getAmount()));
        try{
            accountClient.credit(request.getToAccountNumber(), new BalanceUpdateRequest(request.getAmount()));
        }catch (Exception creditFailure) {
            log.error("Credit failed after debit succeeded, reversing debit for {}",
                    request.getFromAccountNumber(), creditFailure);
            accountClient.credit(request.getFromAccountNumber(), new BalanceUpdateRequest(request.getAmount()));
            throw creditFailure;
        }

        Transaction txn = Transaction.builder()
                .type("TRANSFER")
                .fromAccountNumber(request.getFromAccountNumber())
                .toAccountNumber(request.getToAccountNumber())
                .amount(request.getAmount())
                .status("SUCCESS")
                .build();

        return transactionRepository.save(txn);
    }

    private Transaction depositFallback(AmountRequest request, Throwable t) {
        log.warn("Deposit fallback triggered for {}: {}", request.getAccountNumber(), t.getMessage());

        if (t instanceof FeignException.NotFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Account not found: " + request.getAccountNumber());
        }

        return saveFailedTransaction("DEPOSIT", null, request.getAccountNumber(), request.getAmount(), t);
    }

    private Transaction withdrawFallback(AmountRequest request, Throwable t) {
        log.warn("Withdraw fallback triggered for {}: {}", request.getAccountNumber(), t.getMessage());

        if (t instanceof FeignException.NotFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Account not found: " + request.getAccountNumber());
        }
        if (t instanceof FeignException.BadRequest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient balance in account: " + request.getAccountNumber());
        }

        return saveFailedTransaction("WITHDRAWAL", request.getAccountNumber(), null, request.getAmount(), t);
    }

    private Transaction transferFallback(TransferRequest request, Throwable t) {
        log.warn("Transfer fallback triggered {} -> {}: {}",
                request.getFromAccountNumber(), request.getToAccountNumber(), t.getMessage());

        if (t instanceof FeignException.NotFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "One of the accounts in this transfer was not found");
        }
        if (t instanceof FeignException.BadRequest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insufficient balance in account: " + request.getFromAccountNumber());
        }

        return saveFailedTransaction("TRANSFER", request.getFromAccountNumber(),
                request.getToAccountNumber(), request.getAmount(), t);
    }

    private Transaction saveFailedTransaction(String type, String from, String to,
                                              java.math.BigDecimal amount, Throwable t) {
        Transaction failed = Transaction.builder()
                .type(type)
                .fromAccountNumber(from)
                .toAccountNumber(to)
                .amount(amount)
                .status("FAILED")
                .failureReason("account-service unavailable: " + t.getClass().getSimpleName())
                .build();
        transactionRepository.save(failed);

        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Account service is currently unavailable. Your " + type.toLowerCase() +
                        " could not be completed and has been logged as failed.");
    }

}
