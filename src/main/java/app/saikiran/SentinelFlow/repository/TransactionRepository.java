package app.saikiran.SentinelFlow.repository;

import app.saikiran.SentinelFlow.model.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {
    List<Transaction> findByAccountIdOrderByTimestampDesc(String accountId);
}
