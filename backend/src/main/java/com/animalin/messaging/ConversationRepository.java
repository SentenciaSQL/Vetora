package com.animalin.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);
    @Query("select c from Conversation c join c.participants p where p.id = :userId order by c.updatedAt desc")
    List<Conversation> findByParticipant(Long userId);
    Optional<Conversation> findByIdAndTenantId(Long id, Long tenantId);
}
