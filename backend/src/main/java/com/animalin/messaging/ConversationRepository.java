package com.animalin.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    List<Conversation> findByTenantIdOrderByUpdatedAtDesc(Long tenantId);
    @Query("""
            select c from Conversation c
            where c.id in (
                select c2.id from Conversation c2
                left join c2.participants p
                left join c2.owner o
                where p.id = :userId or o.user.id = :userId
            )
            order by c.updatedAt desc
            """)
    List<Conversation> findByParticipant(Long userId);
    Optional<Conversation> findByIdAndTenantId(Long id, Long tenantId);
}
