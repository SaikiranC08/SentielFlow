package app.saikiran.SentinelFlow.repository;

import app.saikiran.SentinelFlow.model.entity.AccountStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStatsRepository extends JpaRepository<AccountStats, String> {
}
