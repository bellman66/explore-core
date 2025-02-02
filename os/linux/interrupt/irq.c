/*
* interrupt 발생시 사용되는 irq 호출 메소드
* do_irq 값이 수정되어 handle_irq 메소드로 변경
* path : x86/kernel/irq.c
*/
static __always_inline void handle_irq(struct irq_desc *desc,
				       struct pt_regs *regs)
{
	if (IS_ENABLED(CONFIG_X86_64))
		generic_handle_irq_desc(desc);
	else
		__handle_irq(desc, regs);
}
