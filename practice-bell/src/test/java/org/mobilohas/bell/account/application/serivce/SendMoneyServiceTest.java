package org.mobilohas.bell.account.application.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.InstanceOfAssertFactories.map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mobilohas.bell.account.application.port.in.SendMoneyCommand;
import org.mobilohas.bell.account.application.port.out.AccountLock;
import org.mobilohas.bell.account.application.port.out.LoadAccountPort;
import org.mobilohas.bell.account.application.port.out.UpdateAccountStatePort;
import org.mobilohas.bell.account.application.service.MoneyTransferProperties;
import org.mobilohas.bell.account.application.service.SendMoneyService;
import org.mobilohas.bell.account.domain.Account;
import org.mobilohas.bell.account.domain.Account.AccountId;
import org.mobilohas.bell.account.domain.Money;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

class SendMoneyServiceTest {


  private final LoadAccountPort loadAccountPort =
      Mockito.mock(LoadAccountPort.class);

  private final AccountLock accountLock =
      Mockito.mock(AccountLock.class);

  private final UpdateAccountStatePort updateAccountStatePort =
      Mockito.mock(UpdateAccountStatePort.class);

  private final SendMoneyService sendMoneyService =
      new SendMoneyService(loadAccountPort, accountLock, updateAccountStatePort, moneyTransferProperties());


  @Test
  void transactionSucceeds() {
    //given
    Account sourceAccount = givenSourceAccount();
    Account targetAccount = givenTargetAccount();

    givenWithdrawalWillSucceed(sourceAccount);
    givenDepositWillSucceed(targetAccount);

    Money money = Money.of(500L);

    SendMoneyCommand command = new SendMoneyCommand(
        sourceAccount.getId().get(), targetAccount.getId().get(), money);

    //when
    boolean success = sendMoneyService.sendMoney(command);

    //then
    assertThat(success).isTrue();

    AccountId sourceAccountId = sourceAccount.getId().get();
    AccountId targetAccountId = targetAccount.getId().get();

    then(accountLock).should().lockAccount(eq(sourceAccountId));
    then(sourceAccount).should().withdraw(eq(money), eq(targetAccountId));
    then(accountLock).should().releaseAccount(eq(sourceAccountId));

    then(accountLock).should().lockAccount(eq(targetAccountId));
    then(targetAccount).should().deposit(eq(money), eq(sourceAccountId));
    then(accountLock).should().releaseAccount(eq((targetAccountId)));

    thenAccountsHaveBeenUpdated(sourceAccountId, targetAccountId);
  }

  private void thenAccountsHaveBeenUpdated(AccountId... accountIds){
    ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
    then(updateAccountStatePort).should(times(accountIds.length))
        .updateActivities(accountCaptor.capture());

    List<AccountId> updatedAccountIds = accountCaptor.getAllValues()
        .stream()
        .map(Account::getId)
        .map(Optional::get)
        .collect(Collectors.toList());

    for(AccountId accountId : accountIds){
      assertThat(updatedAccountIds).contains(accountId);
    }
  }

  private void givenDepositWillSucceed(Account account) {
    given(account.deposit(any(Money.class), any(AccountId.class)))
        .willReturn(true);
  }

  private void givenWithdrawalWillFail(Account account) {
    given(account.withdraw(any(Money.class), any(AccountId.class)))
        .willReturn(false);
  }

  private void givenWithdrawalWillSucceed(Account account) {
    given(account.withdraw(any(Money.class), any(AccountId.class)))
        .willReturn(true);
  }

  private Account givenTargetAccount(){
    return givenAnAccountWithId(new AccountId(42L));
  }

  private Account givenSourceAccount(){
    return givenAnAccountWithId(new AccountId(41L));
  }

  private Account givenAnAccountWithId(AccountId id) {
    Account account = Mockito.mock(Account.class);
    given(account.getId())
        .willReturn(Optional.of(id));
    given(loadAccountPort.loadAccount(eq(account.getId().get()), any(LocalDateTime.class)))
        .willReturn(account);
    return account;
  }

  private MoneyTransferProperties moneyTransferProperties(){
    return new MoneyTransferProperties(Money.of(Long.MAX_VALUE));
  }

}