/**
 * the warehouse.
 * Knows how data is stored.
 * No business rules.
 *
 * <p>Port + adapters, as in {@code user.repository}:
 * {@code WalletRepository} is the port, {@code JpaWalletRepository} and
 * {@code InMemoryWalletRepository} are the adapters, and
 * {@code SpringDataWalletRepository} is Spring Data's interface that only
 * the JPA adapter is allowed to see.
 */
package com.paynest.wallet.repository;
