package com.ticketya.reservas.repository;

import com.ticketya.reservas.model.ReservaItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservaItemRepository extends JpaRepository<ReservaItem, Long> {
}
