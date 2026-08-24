package com.alzswell.holding.application;

import static com.alzswell.holding.api.FinancialHoldingResponses.*;

import com.alzswell.common.exception.BusinessException;
import com.alzswell.holding.api.FinancialHoldingErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class FinancialHoldingQueryService {
 private static final String DEPOSIT_SELECT="""
   select d.holding_id,d.account_id,a.institution_id,i.display_name institution_name,a.display_name,a.masked_account_number,d.product_type,d.principal_amount,a.current_balance,a.accrued_interest,a.annual_interest_rate,d.opened_on,d.maturity_date,a.account_status,a.currency,d.data_as_of from customer_deposit_holding_snapshot d join customer_account_snapshot a on a.account_id=d.account_id and a.customer_id=d.customer_id join financial_institution i on i.institution_id=a.institution_id
   """;
 private static final String LOAN_SELECT="""
   select l.liability_id,l.institution_id,i.display_name institution_name,l.display_name,l.masked_reference,l.liability_type,d.original_principal,l.outstanding_amount,l.scheduled_amount,l.annual_interest_rate,l.next_due_date,d.started_on,d.maturity_date,d.repayment_method,l.status,l.currency,d.data_as_of from customer_liability_snapshot l join customer_loan_holding_detail_snapshot d on d.loan_id=l.liability_id and d.customer_id=l.customer_id join financial_institution i on i.institution_id=l.institution_id
   """;
 private static final String INVESTMENT_SELECT="""
   select a.investment_account_id,a.institution_id,i.display_name institution_name,a.display_name,a.masked_account_number,a.account_type,a.status,a.cash_balance,a.total_market_value,a.currency,a.data_as_of from customer_investment_account_snapshot a join financial_institution i on i.institution_id=a.institution_id
   """;
 private static final String PENSION_SELECT="""
   select p.holding_id,p.institution_id,i.display_name institution_name,p.display_name,p.masked_contract_reference,p.pension_type,p.status,p.contributed_amount,p.current_value,p.expected_benefit_start_date,p.currency,p.data_as_of from customer_pension_holding_snapshot p join financial_institution i on i.institution_id=p.institution_id
   """;
 private static final String TRUST_SELECT="""
   select t.trust_id,t.institution_id,i.display_name institution_name,t.display_name,t.masked_contract_reference,t.trust_type,t.purpose_code,t.status,t.entrusted_principal,t.current_value,t.beneficiary_count,t.started_on,t.maturity_date,t.next_review_date,t.currency,t.data_as_of from customer_trust_holding_snapshot t join financial_institution i on i.institution_id=t.institution_id
   """;
 private final JdbcTemplate jdbc;
 public FinancialHoldingQueryService(JdbcTemplate jdbc){this.jdbc=jdbc;}

 public DepositList deposits(String customerId){List<Deposit> items=jdbc.query(DEPOSIT_SELECT+" where d.customer_id=? order by d.maturity_date,d.holding_id",this::depositRow,customerId);return new DepositList(items,items.size(),true,false);}
 public DepositDetail deposit(String customerId,UUID id){List<Deposit> rows=jdbc.query(DEPOSIT_SELECT+" where d.customer_id=? and d.holding_id=?",this::depositRow,customerId,id);if(rows.size()!=1)throw new BusinessException(FinancialHoldingErrorCode.DEPOSIT_NOT_FOUND);BigDecimal maturity=jdbc.queryForObject("select expected_maturity_amount from customer_deposit_holding_snapshot where holding_id=?",BigDecimal.class,id);return new DepositDetail(rows.getFirst(),maturity,false,true,false,false);}
 public LoanList loans(String customerId){List<Loan> items=jdbc.query(LOAN_SELECT+" where l.customer_id=? order by l.next_due_date,l.liability_id",this::loanRow,customerId);return new LoanList(items,items.size(),true,false);}
 public LoanDetail loan(String customerId,UUID id){return new LoanDetail(ownedLoan(customerId,id),false,true,false,false);}
 public RepaymentSchedule schedule(String customerId,UUID id){ownedLoan(customerId,id);List<RepaymentInstallment> items=jdbc.query("""
   select installment_id,installment_number,due_date,principal_amount,interest_amount,status,data_as_of
   from loan_repayment_schedule_snapshot where loan_id=? order by installment_number,installment_id
   """,(rs,n)->new RepaymentInstallment(rs.getObject("installment_id",UUID.class),rs.getInt("installment_number"),rs.getObject("due_date",LocalDate.class),rs.getBigDecimal("principal_amount"),rs.getBigDecimal("interest_amount"),rs.getBigDecimal("principal_amount").add(rs.getBigDecimal("interest_amount")),rs.getString("status"),rs.getObject("data_as_of",LocalDate.class)),id);return new RepaymentSchedule(id,items,items.size(),false,true,false,false);}
 public InvestmentAccountList investments(String customerId){List<InvestmentAccount> items=jdbc.query(INVESTMENT_SELECT+" where a.customer_id=? order by a.status,a.display_name,a.investment_account_id",this::investmentRow,customerId);return new InvestmentAccountList(items,items.size(),true,false);}
 public Portfolio portfolio(String customerId,UUID id){InvestmentAccount account=ownedInvestment(customerId,id);List<Allocation> allocations=jdbc.query("""
   select asset_class,sum(market_value) market_value from investment_position_snapshot where investment_account_id=? group by asset_class order by asset_class
   """,(rs,n)->{BigDecimal value=rs.getBigDecimal("market_value");BigDecimal invested=account.totalMarketValue().subtract(account.cashBalance());BigDecimal weight=invested.signum()==0?BigDecimal.ZERO:value.multiply(BigDecimal.valueOf(100)).divide(invested,2,RoundingMode.HALF_UP);return new Allocation(rs.getString("asset_class"),value,weight);},id);return new Portfolio(id,account.cashBalance(),account.totalMarketValue().subtract(account.cashBalance()),account.totalMarketValue(),allocations,false,true,false,false);}
 public PositionList positions(String customerId,UUID id){ownedInvestment(customerId,id);List<Position> items=jdbc.query("""
   select position_id,asset_class,instrument_name,masked_instrument_code,quantity,average_purchase_price,current_price,market_value,unrealized_profit_loss,currency,data_as_of
   from investment_position_snapshot where investment_account_id=? order by asset_class,position_id
   """,(rs,n)->new Position(rs.getObject("position_id",UUID.class),rs.getString("asset_class"),rs.getString("instrument_name"),rs.getString("masked_instrument_code"),rs.getBigDecimal("quantity"),rs.getBigDecimal("average_purchase_price"),rs.getBigDecimal("current_price"),rs.getBigDecimal("market_value"),rs.getBigDecimal("unrealized_profit_loss"),rs.getString("currency"),rs.getObject("data_as_of",LocalDate.class)),id);return new PositionList(id,items,items.size(),false,true,false,false);}
 public PensionHoldingList pensions(String customerId){List<PensionHolding> items=jdbc.query(PENSION_SELECT+" where p.customer_id=? order by p.status,p.expected_benefit_start_date,p.holding_id",this::pensionRow,customerId);return new PensionHoldingList(items,items.size(),true,false);}
 public PensionProjection pensionProjection(String customerId,UUID id){ownedPension(customerId,id);List<PensionScenario> items=jdbc.query("""
   select projection_id,scenario_code,assumed_annual_return,projected_value,projected_monthly_benefit,benefit_start_date,calculated_on
   from pension_projection_snapshot where holding_id=? order by scenario_code,projection_id
   """,(rs,n)->new PensionScenario(rs.getObject("projection_id",UUID.class),rs.getString("scenario_code"),rs.getBigDecimal("assumed_annual_return"),rs.getBigDecimal("projected_value"),rs.getBigDecimal("projected_monthly_benefit"),rs.getObject("benefit_start_date",LocalDate.class),rs.getObject("calculated_on",LocalDate.class)),id);return new PensionProjection(id,items,items.size(),"합성 금융사 가정에 따른 예시이며 실제 수익이나 지급액을 보장하지 않습니다.",false,false,false,true,false,false);}
 public TrustHoldingList trusts(String customerId){List<TrustHolding> items=jdbc.query(TRUST_SELECT+" where t.customer_id=? order by t.status,t.display_name,t.trust_id",this::trustRow,customerId);return new TrustHoldingList(items,items.size(),true,false);}
 public TrustHoldingDetail trust(String customerId,UUID id){List<TrustHolding> rows=jdbc.query(TRUST_SELECT+" where t.customer_id=? and t.trust_id=?",this::trustRow,customerId,id);if(rows.size()!=1)throw new BusinessException(FinancialHoldingErrorCode.TRUST_NOT_FOUND);return new TrustHoldingDetail(rows.getFirst(),false,false,true,false,false);}

 private Loan ownedLoan(String customerId,UUID id){List<Loan> rows=jdbc.query(LOAN_SELECT+" where l.customer_id=? and l.liability_id=?",this::loanRow,customerId,id);if(rows.size()!=1)throw new BusinessException(FinancialHoldingErrorCode.LOAN_NOT_FOUND);return rows.getFirst();}
 private InvestmentAccount ownedInvestment(String customerId,UUID id){List<InvestmentAccount> rows=jdbc.query(INVESTMENT_SELECT+" where a.customer_id=? and a.investment_account_id=?",this::investmentRow,customerId,id);if(rows.size()!=1)throw new BusinessException(FinancialHoldingErrorCode.INVESTMENT_NOT_FOUND);return rows.getFirst();}
 private PensionHolding ownedPension(String customerId,UUID id){List<PensionHolding> rows=jdbc.query(PENSION_SELECT+" where p.customer_id=? and p.holding_id=?",this::pensionRow,customerId,id);if(rows.size()!=1)throw new BusinessException(FinancialHoldingErrorCode.PENSION_NOT_FOUND);return rows.getFirst();}
 private Deposit depositRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Deposit(r.getObject("holding_id",UUID.class),r.getObject("account_id",UUID.class),r.getString("institution_id"),r.getString("institution_name"),r.getString("display_name"),r.getString("masked_account_number"),r.getString("product_type"),r.getBigDecimal("principal_amount"),r.getBigDecimal("current_balance"),r.getBigDecimal("accrued_interest"),r.getBigDecimal("annual_interest_rate"),r.getObject("opened_on",LocalDate.class),r.getObject("maturity_date",LocalDate.class),r.getString("account_status"),r.getString("currency"),r.getObject("data_as_of",LocalDate.class));}
 private Loan loanRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Loan(r.getObject("liability_id",UUID.class),r.getString("institution_id"),r.getString("institution_name"),r.getString("display_name"),r.getString("masked_reference"),r.getString("liability_type"),r.getBigDecimal("original_principal"),r.getBigDecimal("outstanding_amount"),r.getBigDecimal("scheduled_amount"),r.getBigDecimal("annual_interest_rate"),r.getObject("next_due_date",LocalDate.class),r.getObject("started_on",LocalDate.class),r.getObject("maturity_date",LocalDate.class),r.getString("repayment_method"),r.getString("status"),r.getString("currency"),r.getObject("data_as_of",LocalDate.class));}
 private InvestmentAccount investmentRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new InvestmentAccount(r.getObject("investment_account_id",UUID.class),r.getString("institution_id"),r.getString("institution_name"),r.getString("display_name"),r.getString("masked_account_number"),r.getString("account_type"),r.getString("status"),r.getBigDecimal("cash_balance"),r.getBigDecimal("total_market_value"),r.getString("currency"),r.getObject("data_as_of",LocalDate.class));}
 private PensionHolding pensionRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new PensionHolding(r.getObject("holding_id",UUID.class),r.getString("institution_id"),r.getString("institution_name"),r.getString("display_name"),r.getString("masked_contract_reference"),r.getString("pension_type"),r.getString("status"),r.getBigDecimal("contributed_amount"),r.getBigDecimal("current_value"),r.getObject("expected_benefit_start_date",LocalDate.class),r.getString("currency"),r.getObject("data_as_of",LocalDate.class));}
 private TrustHolding trustRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new TrustHolding(r.getObject("trust_id",UUID.class),r.getString("institution_id"),r.getString("institution_name"),r.getString("display_name"),r.getString("masked_contract_reference"),r.getString("trust_type"),r.getString("purpose_code"),r.getString("status"),r.getBigDecimal("entrusted_principal"),r.getBigDecimal("current_value"),r.getInt("beneficiary_count"),r.getObject("started_on",LocalDate.class),r.getObject("maturity_date",LocalDate.class),r.getObject("next_review_date",LocalDate.class),r.getString("currency"),r.getObject("data_as_of",LocalDate.class));}
}
