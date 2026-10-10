package com.match.SwipeAI.repository;

import com.match.SwipeAI.model.MarketingEmailCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MarketingEmailCampaignRepository extends JpaRepository<MarketingEmailCampaign, UUID> {
    List<MarketingEmailCampaign> findTop30ByOrderByCreatedAtDesc();
}
