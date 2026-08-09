package com.bankingapp.transaction_service.repository;

import com.bankingapp.transaction_service.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByFromAccountNumberOrToAccountNumber(String from, String to);
}
