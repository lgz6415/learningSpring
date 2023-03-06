package learning.spring.binarytea.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.joda.money.Money;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Amount {
    @Column(name = "amount_discount")
    private int discount;

    @Column(name = "amount_total")
    @Type(type = "learning.spring.binarytea.support.MoneyType")
    private Money totalAmount;

    @Column(name = "amount_pay")
    @Type(type = "learning.spring.binarytea.support.MoneyType")
    private Money payAmount;
}
