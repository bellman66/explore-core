/*
* location - net/ipv4/ip_input.c
*
* IP 입력 Entry point
*
* 패킷 입력이 네트워크 인터페이스로 들어올때 실행되는 리눅스 커널 함수
* 네트워크 인터페이스로부터 받은 IP 컴사후 상위 계층으로 전달할지 결정
*
*   skb : 수신된 패킷 Buff 구조체
*   dev : 패킷이 들어온 네트워크 인터페이스 (eth0, wlan0 등).
*   pt : 패킷 유형 정보를 포함.
*   orig_dev : 원래 수신한 인터페이스.
*/
int ip_rcv(struct sk_buff *skb, struct net_device *dev, struct packet_type *pt,
	   struct net_device *orig_dev)
{
	struct net *net = dev_net(dev);

	skb = ip_rcv_core(skb, net);
	if (skb == NULL)
		return NET_RX_DROP;

	return NF_HOOK(NFPROTO_IPV4, NF_INET_PRE_ROUTING,
		       net, NULL, skb, dev, NULL,
		       ip_rcv_finish);
}
