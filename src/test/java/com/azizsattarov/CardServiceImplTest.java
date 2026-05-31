package com.azizsattarov;

import com.azizsattarov.corebanking.account.Account;
import com.azizsattarov.corebanking.account.AccountRepository;
import com.azizsattarov.corebanking.account.AccountStatus;
import com.azizsattarov.corebanking.card.*;
import com.azizsattarov.corebanking.card.dto.CardResponse;
import com.azizsattarov.corebanking.card.dto.IssueCardRequest;
import com.azizsattarov.corebanking.card.dto.UpdateCardRequest;
import com.azizsattarov.corebanking.customer.Customer;
import com.azizsattarov.corebanking.exception.BadRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CardServiceImpl: Luhn-valid 16-digit number generation,
 * cards-per-account ceiling, holder-name handling, issuance state rules,
 * and the ATM ownership check on card listing.
 */
@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    @Mock
    CardRepository cardRepository;
    @Mock AccountRepository accountRepository;
    @Mock
    CardEmailNotificationService emailService;

    CardServiceImpl service;

    private Account account;

    @BeforeEach
    void setUp() {
        service = new CardServiceImpl(cardRepository, accountRepository, emailService);
        Customer customer = new Customer();
        customer.setFirstName("John");
        customer.setLastName("Doe");
        account = new Account("DE123", new BigDecimal("100.00"));
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCustomer(customer);
        setAccountId(account, 1L);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Issuance ────────────────────────────────────────────────────────────---

    @Test
    void issueCard_generatesLuhnValid16DigitNumber() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findByAccountId(1L)).thenReturn(new ArrayList<>());
        when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        CardResponse resp = service.issueCard(1L, new IssueCardRequest(null));

        assertEquals(16, resp.cardNumber().length());
        assertTrue(resp.cardNumber().startsWith("62260099"));
        assertTrue(isValidLuhn(resp.cardNumber()), "card number must be Luhn-valid");
    }

    @Test
    void issueCard_defaultsHolderNameToCustomerUppercase() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findByAccountId(1L)).thenReturn(new ArrayList<>());
        when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        CardResponse resp = service.issueCard(1L, new IssueCardRequest(null));
        assertEquals("JOHN DOE", resp.holderName());
    }

    @Test
    void issueCard_issuedWithoutPin() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findByAccountId(1L)).thenReturn(new ArrayList<>());
        when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        service.issueCard(1L, new IssueCardRequest(null));

        // the saved card must NOT carry a PIN — customer sets it at the ATM
        verify(cardRepository).save(argThat(c -> c.getPinHash() == null));
    }

    @Test
    void issueCard_rejectsInactiveAccount() {
        account.setAccountStatus(AccountStatus.FROZEN);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        assertThrows(BadRequestException.class,
                () -> service.issueCard(1L, new IssueCardRequest(null)));
    }

    @Test
    void issueCard_blocksFourthActiveCard() {
        List<Card> existing = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Card c = new Card("621" + i, "JOHN DOE", LocalDate.now().plusYears(3));
            c.setCardStatus(CardStatus.ACTIVE);
            existing.add(c);
        }
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findByAccountId(1L)).thenReturn(existing);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.issueCard(1L, new IssueCardRequest(null)));
        assertTrue(ex.getMessage().contains("Maximum allowed"));
        verify(cardRepository, never()).save(any());
    }

    @Test
    void issueCard_cancelledAndExpiredCardsFreeSlots() {
        List<Card> existing = new ArrayList<>();
        Card c1 = new Card("6211", "JOHN DOE", LocalDate.now().plusYears(3));
        c1.setCardStatus(CardStatus.CANCELLED);
        Card c2 = new Card("6212", "JOHN DOE", LocalDate.now().plusYears(3));
        c2.setCardStatus(CardStatus.EXPIRED);
        existing.add(c1);
        existing.add(c2);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findByAccountId(1L)).thenReturn(existing);
        when(cardRepository.existsByCardNumber(anyString())).thenReturn(false);
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.issueCard(1L, new IssueCardRequest(null)));
    }

    // ── Status update ──────────────────────────────────────────────────────---

    @Test
    void updateCardStatus_setsBlockedTimestamp() {
        Card card = new Card("6211", "JOHN DOE", LocalDate.now().plusYears(3));
        card.setCardStatus(CardStatus.ACTIVE);
        when(cardRepository.findById(5L)).thenReturn(Optional.of(card));
        when(cardRepository.save(any(Card.class))).thenAnswer(inv -> inv.getArgument(0));
        // attach an account so toResponse() can read account fields
        card.setAccount(account);

        service.updateCardStatus(5L, new UpdateCardRequest(CardStatus.BLOCKED));

        assertEquals(CardStatus.BLOCKED, card.getCardStatus());
        assertNotNull(card.getBlockedAt());
    }

    // ── Ownership on card listing ───────────────────────────────────────────--

    @Test
    void getCardsByAccount_rejectsAtmPrincipalMismatch() {
        setAtmPrincipal("ATM_DE999");   // session for a different account
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account)); // account DE123
        assertThrows(BadRequestException.class, () -> service.getCardsByAccount(1L));
    }

    @Test
    void getCardsByAccount_allowsMatchingAtmPrincipal() {
        setAtmPrincipal("ATM_DE123");
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findByAccountId(1L)).thenReturn(new ArrayList<>());
        assertDoesNotThrow(() -> service.getCardsByAccount(1L));
    }

    @Test
    void cancelCard_rejectsCardFromAnotherAccount() {
        Account other = new Account("DE999", BigDecimal.ZERO);
        setAccountId(other, 2L);
        Card card = new Card("6211", "X", LocalDate.now().plusYears(3));
        card.setAccount(other);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(cardRepository.findById(5L)).thenReturn(Optional.of(card));
        assertThrows(BadRequestException.class, () -> service.cancelCard(1L, 5L));
    }

    // ── helpers ────────────────────────────────────────────────────────────---

    private static boolean isValidLuhn(String number) {
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int d = Character.getNumericValue(number.charAt(i));
            if (alt) { d *= 2; if (d > 9) d -= 9; }
            sum += d;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    private static void setAccountId(Account a, long id) {
        try {
            var f = Account.class.getDeclaredField("accountId");
            f.setAccessible(true);
            f.set(a, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setAtmPrincipal(String name) {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                name, null, java.util.List.of());
        var ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
    }
}
