/*
* location - include/linux/netdevice.c
*
* Net namespace inlines
*/
static inline
struct net *dev_net(const struct net_device *dev)
{
	return read_pnet(&dev->nd_net);
}

/*
* location - include/net/net_namespace.h
*
* Net namespace inlines
*/
static inline struct net *read_pnet(const possible_net_t *pnet)
{
#ifdef CONFIG_NET_NS
	return rcu_dereference_protencted(pnet->net, true);
#else
	return &init_net;
#endif
}

/**
 * location - include/linux/rcupdate.h
 *
 * rcu_dereference_protected() - fetch RCU pointer when updates prevented
 * @p: The pointer to read, prior to dereferencing
 * @c: The conditions under which the dereference will take place
 *
 * Return the value of the specified RCU-protected pointer, but omit
 * the READ_ONCE().  This is useful in cases where update-side locks
 * prevent the value of the pointer from changing.  Please note that this
 * primitive does *not* prevent the compiler from repeating this reference
 * or combining it with other references, so it should not be used without
 * protection of appropriate locks.
 *
 * This function is only for update-side use.  Using this function
 * when protected only by rcu_read_lock() will result in infrequent
 * but very ugly failures.
 */
#define rcu_dereference_protected(p, c) \
	__rcu_dereference_protected((p), __UNIQUE_ID(rcu), (c), __rcu)

/**
* location - include/linux/rcupdate.h
*
*/
#define __rcu_dereference_protected(p, local, c, space) \
({ \
	RCU_LOCKDEP_WARN(!(c), "suspicious rcu_dereference_protected() usage"); \
	rcu_check_sparse(p, space); \
	((typeof(*p) __force __kernel *)(p)); \
})


/**
 * location - include/linux/rcupdate.h
 *
 * RCU_LOCKDEP_WARN - emit lockdep splat if specified condition is met
 * @c: condition to check
 * @s: informative message
 *
 * This checks debug_lockdep_rcu_enabled() before checking (c) to
 * prevent early boot splats due to lockdep not yet being initialized,
 * and rechecks it after checking (c) to prevent false-positive splats
 * due to races with lockdep being disabled.  See commit 3066820034b5dd
 * ("rcu: Reject RCU_LOCKDEP_WARN() false positives") for more detail.
 */
#define RCU_LOCKDEP_WARN(c, s)						\
	do {								\
		static bool __section(".data..unlikely") __warned;	\
		if (debug_lockdep_rcu_enabled() && (c) &&		\
		    debug_lockdep_rcu_enabled() && !__warned) {		\
			__warned = true;				\
			lockdep_rcu_suspicious(__FILE__, __LINE__, s);	\
		}							\
	} while (0)

#ifndef CONFIG_PREEMPT_RCU

/*
 * location - include/linux/rcupdate.h
 *
 * Helper functions for rcu_dereference_check(), rcu_dereference_protected()
 * and rcu_assign_pointer().  Some of these could be folded into their
 * callers, but they are left separate in order to ease introduction of
 * multiple pointers markings to match different RCU implementations
 * (e.g., __srcu), should this make sense in the future.
 */

#ifdef __CHECKER__
#define rcu_check_sparse(p, space) \
	((void)(((typeof(*p) space *)p) == p))
#else /* #ifdef __CHECKER__ */
#define rcu_check_sparse(p, space)
#endif /* #else #ifdef __CHECKER__ */
