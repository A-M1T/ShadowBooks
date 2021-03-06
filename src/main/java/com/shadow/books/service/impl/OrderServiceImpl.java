package com.shadow.books.service.impl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.shadow.books.Constants.DBConstants;
import com.shadow.books.domain.Address;
import com.shadow.books.domain.Item;
import com.shadow.books.domain.LineItem;
import com.shadow.books.domain.Order;
import com.shadow.books.repository.ItemRepository;
import com.shadow.books.repository.LineItemRepository;
import com.shadow.books.repository.OrderRepository;
import com.shadow.books.service.AddressService;
import com.shadow.books.service.LineItemService;
import com.shadow.books.service.OrderService;

@Service
@Transactional
public class OrderServiceImpl implements OrderService {

	Logger logger = LogManager.getLogger(this.getClass());

	@Autowired
	OrderRepository orderRepository;

	@Autowired
	LineItemRepository lineItemRepository;

	@Autowired
	AddressService addressService;

	@Autowired
	ItemRepository itemRepository;

	@Autowired
	LineItemService lineItemService;

	@Override
	public Order add(Order order) {

		order.setDeleted(false);
		order.setStatus(DBConstants.PENDING);
		order.setCreatedOn(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
		order.setModifiedOn(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
		boolean proceedWithOrder = true;

		if (order.getItem() == null) {
			// Flow from Cart

			Double totalAmount = 0.0d;
			List<LineItem> cartItems = lineItemRepository.findByUserIdAndStatus(order.getUserId(), DBConstants.IN_CART);

			List<Item> entities = new ArrayList<Item>();

			if (!cartItems.isEmpty()) {
				for (LineItem cartItem : cartItems) {
					Optional<Item> optItem = itemRepository.findById(cartItem.getProductId());

					if (!optItem.isPresent() || optItem.get().getQuantity() < cartItem.getQuantity()) {
						proceedWithOrder = false;
						break;
					}
					optItem.get().setQuantity(optItem.get().getQuantity() - cartItem.getQuantity());
					if (optItem.get().getQuantity() <= 0) {
						optItem.get().setStatus(DBConstants.UNAVAILABLE);

					}
					entities.add(optItem.get());
				}

				if (proceedWithOrder) {
					totalAmount = cartItems.stream().collect(Collectors.summingDouble(LineItem::getAmount));
					order.setTotalAmount(totalAmount);
					order = orderRepository.save(order);

					// UPDATING QTY AND STATUS IN ITEMS
					itemRepository.saveAll(entities);

					lineItemRepository.setOrderIdAndStatus(order.getId(), order.getUserId());

				} else {
					order.setStatus(DBConstants.BOOK_OUT_OF_STOCK);
				}

			} else {
				order.setStatus(DBConstants.BOOK_OUT_OF_STOCK);
			}

		} else {
			// Flow from Buy Now

			Optional<Item> optItem = itemRepository.findById(order.getItem().getId());
			if (optItem.isPresent() && optItem.get().getQuantity() >= order.getItem().getQuantity()) {

				addShoppingCartDetailsAndPlaceOrder(optItem, order);
				optItem.get().setQuantity(optItem.get().getQuantity() - order.getItem().getQuantity());

				if (optItem.get().getQuantity() <= 0) {
					optItem.get().setStatus(DBConstants.UNAVAILABLE);
				}

				itemRepository.save(optItem.get());

			} else {
				order.setStatus(DBConstants.BOOK_OUT_OF_STOCK);
			}

		}
		return order;
	}

	private void addShoppingCartDetailsAndPlaceOrder(Optional<Item> optItem, Order order) {

		Double discount = (double) (optItem.get().getPrice() * optItem.get().getDiscount() / 100);
		double unitAmount = optItem.get().getPrice() - discount;

		order.setTotalAmount(unitAmount * order.getItem().getQuantity());
		order = orderRepository.save(order);

		LineItem lineItem = new LineItem();

		float amount = (optItem.get().getPrice() * optItem.get().getDiscount() / 100);
		float unitPrice = optItem.get().getPrice() - amount;
		lineItem.setUnitPrice(unitPrice);
		lineItem.setOrderId(order.getId());
		lineItem.setProductId(order.getItem().getId());
		lineItem.setQuantity(order.getItem().getQuantity());
		lineItem.setStatus(DBConstants.PENDING);
		lineItem.setName(optItem.get().getName());
		lineItem.setAmount(unitPrice * order.getItem().getQuantity());
		lineItem.setUserId(order.getUserId());
		lineItem.setCreatedOn(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
		lineItem.setModifiedOn(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
		lineItem.setDeleted(false);

		logger.info("LINE ITEM :: " + lineItem);

		lineItemRepository.save(lineItem);
	}

	@Override
	public Optional<Order> updateDelevieryAddressByOrderId(Long id, Address address) {
		address = addressService.add(address);
		Optional<Order> optionalOrder = orderRepository.findById(id);
		if (optionalOrder.isPresent()) {
			optionalOrder.get().setId(optionalOrder.get().getId());
			optionalOrder.get().setUserId(optionalOrder.get().getUserId());
			optionalOrder.get().setAddress(formatDeliveryAddress(address));
			optionalOrder.get().setContactNo(optionalOrder.get().getContactNo());
			optionalOrder.get().setTotalAmount(optionalOrder.get().getTotalAmount());
			optionalOrder.get().setStatus(optionalOrder.get().getStatus());
			return Optional.ofNullable(orderRepository.save(optionalOrder.get()));
		}
		return optionalOrder;
	}

	private String formatDeliveryAddress(Address address) {
		return address.getHouseNumber() + " " + address.getStreet() + " " + address.getArea() + " "
				+ address.getLandmark();
	}

	@Override
	public Optional<Order> updateOrderStatus(Order order) {
		Optional<Order> optionalOrder = orderRepository.findById(order.getId());
		Order fetchedorder = null;
		if (optionalOrder.isPresent()) {
			fetchedorder = optionalOrder.get();
			fetchedorder.setStatus(order.getStatus());
			fetchedorder = orderRepository.save(fetchedorder);
		}

		return Optional.ofNullable(fetchedorder);
	}

	@Override
	public Page<Order> findOrdersByUserId(long userId, Pageable page) {

//		Page<Order> pageOrder = orderRepository.findByUserIdAndStatusNotInIgnoreCase(userId, DBConstants.CANCELLED,
//				page);
		Page<Order> pageOrder = orderRepository.findByUserIdOrderByIdDesc(userId, page);

		if (!pageOrder.isEmpty()) {
			pageOrder.forEach(order -> {

				List<LineItem> lineItems = lineItemRepository.findByOrderId(order.getId());
				lineItems.forEach(lineItem -> {
					order.getName().add(lineItem.getName());

				});
			});
		}
		return pageOrder;
	}

	@Override
	public List<LineItem> findOrderById(long orderId) {

		List<LineItem> orderedItems = lineItemRepository.findByOrderId(orderId);
		orderedItems.forEach(orderedItem -> {
			Optional<Item> optItem = itemRepository.findById(orderedItem.getProductId());

			orderedItem.setName(optItem.get().getName());
			orderedItem.setLanguage(optItem.get().getLanguage());
			orderedItem.setImageUrl(optItem.get().getImageUrl());
		});
		return orderedItems;

	}

	@Override
	public Page<Order> findAll(String status, Pageable pageable) {
		if (status.isEmpty()) {
			return orderRepository.findAll(pageable);
		}
		return orderRepository.findByStatus(status, pageable);
	}

}
