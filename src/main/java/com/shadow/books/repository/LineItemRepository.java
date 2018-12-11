package com.shadow.books.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.shadow.books.domain.LineItem;

@Repository
public interface LineItemRepository extends JpaRepository<LineItem, Long> {

	List<LineItem> findByUserIdAndStatusAndOrderIdIsNull(Long userId, String string);
	
	List<LineItem> findByOrderId(Long orderId);
	
	List<LineItem> findByUserIdAndStatus(Long userId, String status);

	@Transactional
	@Modifying
	@Query("update LineItem li set li.status= 'ordered', li.orderId=:orderId where li.status='added' and li.userId = :userId")
	void setOrderIdAndStatus(@Param("orderId") Long orderId, @Param("userId") Long userId);

}