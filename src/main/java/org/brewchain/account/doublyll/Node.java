package org.brewchain.account.doublyll;

public class Node<T> {
	public T data;
	public int num;
	public Node<T> next;
	public Node<T> prev;

	public Node(T v, int number) {
		data = v;
		num = number;
		next = null;
		prev = null;
	}
}