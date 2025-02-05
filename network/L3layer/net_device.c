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
