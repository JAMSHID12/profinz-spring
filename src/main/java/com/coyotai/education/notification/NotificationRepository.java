package com.coyotai.education.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Fresh PENDING rows plus FAILED rows with retries left whose back-off has elapsed. */
    @Query("""
            select n from Notification n
            where n.scheduledAt <= :now
              and (n.status = com.coyotai.education.notification.Notification.Status.PENDING
                   or (n.status = com.coyotai.education.notification.Notification.Status.FAILED and n.retryCount < :maxRetries))
            order by n.scheduledAt asc
            """)
    List<Notification> findDueForSending(@Param("now") Instant now, @Param("maxRetries") int maxRetries,
                                         Pageable pageable);

    @Query("""
            select n from Notification n
            left join fetch n.student
            where (:status is null or n.status = :status)
              and (:channel is null or n.channel = :channel)
              and (:event is null or n.eventType = :event)
              and (:studentId is null or n.student.id = :studentId)
            order by n.createdAt desc, n.id desc
            """)
    Page<Notification> search(@Param("status") Notification.Status status,
                              @Param("channel") NotificationChannel channel,
                              @Param("event") NotificationEvent event,
                              @Param("studentId") Long studentId,
                              Pageable pageable);

    @Query("""
            select n from Notification n left join fetch n.student where n.channel <> com.coyotai.education.notification.NotificationChannel.IN_APP
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findRecentExternal(Pageable pageable);

    @Query("""
            select n from Notification n
            where n.channel = com.coyotai.education.notification.NotificationChannel.IN_APP
              and n.recipientUserId = :userId
              and n.status = com.coyotai.education.notification.Notification.Status.SENT
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findInbox(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select count(n) from Notification n
            where n.channel = com.coyotai.education.notification.NotificationChannel.IN_APP
              and n.recipientUserId = :userId and n.readAt is null
              and n.status = com.coyotai.education.notification.Notification.Status.SENT
            """)
    long countUnread(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.recipientUserId = :userId and n.readAt is null
              and n.channel = com.coyotai.education.notification.NotificationChannel.IN_APP
            """)
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    long countByStatus(Notification.Status status);

    long countByStudentIdAndEventTypeAndCreatedAtAfter(Long studentId, NotificationEvent eventType, Instant after);

    @Query("select n.status, count(n) from Notification n where n.createdAt between :from and :to group by n.status")
    List<Object[]> countByStatusBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select n.eventType, count(n) from Notification n where n.createdAt between :from and :to group by n.eventType")
    List<Object[]> countByEventBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select n.channel, count(n) from Notification n where n.createdAt between :from and :to group by n.channel")
    List<Object[]> countByChannelBetween(@Param("from") Instant from, @Param("to") Instant to);
}
