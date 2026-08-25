package app.saikiran.SentinelFlow.repository;

import app.saikiran.SentinelFlow.model.entity.FlaggedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FlaggedTransactionRepository extends JpaRepository<FlaggedTransaction, String> {

    List<FlaggedTransaction> findAllByOrderByCreatedAtDesc();

    List<FlaggedTransaction> findByAccountIdOrderByCreatedAtDesc(String accountId);
}
