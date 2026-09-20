package com.animalin.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    long countByConversation_TenantIdAndReadAtIsNullAndSenderIdNot(Long tenantId, Long userId);
    long countByConversationIdAndReadAtIsNullAndSenderIdNot(Long conversationId, Long userId);
}
