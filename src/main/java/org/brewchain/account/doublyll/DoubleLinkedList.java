package org.brewchain.account.doublyll;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.WaitBlockHashMapDB;
import org.brewchain.account.util.ALock;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Block_Cache_DLL")
@Slf4j
@Data
public class DoubleLinkedList<T> implements ActorService {

	private Node<T> first = null;
	private Node<T> last = null;
	private int count = 0;

	public DoubleLinkedList() {
		first = null;
		last = null;
		count = 0;
	}

	public void insertFirst(T elem, int number) {
		Node<T> oNode = new Node<T>(elem, number);
		if (first == null) {
			first = oNode;
		} else {
			oNode.next = first;
			first.prev = oNode;
			first = oNode;
		}

		count++;
	}

	public void insertLast(T elem, int number) {
		Node<T> oNode = new Node<T>(elem, number);
		if (last == null) {
			last = oNode;
		} else {
			oNode.prev = last;
			last.next = oNode;
			last = oNode;
		}

		count++;
	}

	public boolean insertAfter(T elem, int number, T target) {
		Node<T> data = new Node<T>(elem, number);
		Node<T> cur = first;
		while (cur != null) {
			if (cur.data.equals(target)) {
				data.next = cur.next;
				data.prev = cur;
				if (cur == last)
					last = data;
				else
					cur.next.prev = data;
				cur.next = data;
				count++;
				return true;
			}
			cur = cur.next;
		}
		return false;
	}

	public T removeFirst() {
		T o = first.data;
		if (last == first) {
			last = null;
			first = null;
		} else {
			first = first.next;
			first.prev = null;
		}
		count--;
		return o;
	}

	public T removeLast() {
		T o = last.data;
		if (last == first) {
			last = null;
			first = null;
		} else {
			last = last.prev;
			last.next = null;
		}
		count--;
		return o;
	}

	public T remove(T elem) {
		T o = null;
		Node<T> egungoa = first;

		while ((egungoa != null) && (o == null)) {
			if (egungoa.data.equals(elem)) {
				o = egungoa.data;
				if (egungoa == first) {
					this.removeFirst();
				} else if (egungoa == last) {
					this.removeLast();
				} else {
					egungoa.prev.next = egungoa.next;
					egungoa.next.prev = egungoa.prev;
					count--;
				}
			} else {
				egungoa = egungoa.next;
			}

		}
		return o;
	}

	public T first() {
		if (isEmpty())
			return null;
		else
			return first.data;
	}

	public T last() {
		if (isEmpty())
			return null;
		else
			return last.data;
	}

	public boolean contains(T elem) {
		if (isEmpty())
			return false;

		Node<T> current = first;

		while ((current != null) && !elem.equals(current.data))
			current = current.next;
		if (current == null)
			return false;
		else
			return elem.equals(current.data);
	}

	public T find(T pElementua) {
		T elementua = null;

		Iterator<T> it = iterator();
		boolean topatua = false;

		while (it.hasNext() && !topatua) {
			elementua = it.next();
			if (pElementua.equals(elementua)) {
				topatua = true;
			}
		}
		if (topatua) {
			return elementua;
		} else {
			return null;
		}

	}

	public boolean isEmpty() {
		return first == null;
	}

	public int size() {
		return count;
	}

	public Iterator<T> iterator() {
		return new ListIterator();
	}

	public Iterator<T> reverseIterator() {
		return new ReverseListIterator();
	}

	private class ListIterator implements Iterator<T> {

		private Node<T> egungoElementua = first;

		@Override
		public boolean hasNext() {
			return (egungoElementua != null);
		}

		@Override
		public T next() {
			if (!hasNext())
				throw new NoSuchElementException();
			T t = egungoElementua.data;
			egungoElementua = egungoElementua.next;
			return t;
		}

		@Override
		public void remove() {
			// TODO Auto-generated method stub

		}

	}

	private class ReverseListIterator implements Iterator<T> {

		private Node<T> egungoElementua = last;

		@Override
		public boolean hasNext() {
			return (egungoElementua != null);
		}

		@Override
		public T next() {
			if (!hasNext())
				throw new NoSuchElementException();
			T t = egungoElementua.data;
			egungoElementua = egungoElementua.prev;
			return t;
		}

		@Override
		public void remove() {
			// TODO Auto-generated method stub

		}

	}

	public void adabegiakInprimatu() {
		System.out.println(this.toString());
	}

	@Override
	public String toString() {
		String result = new String();
		Iterator<T> it = iterator();
		while (it.hasNext()) {
			T elem = it.next();
			result = result + "[" + elem.toString() + "] \n";
		}
		return "SimpleLinkedList " + result;
	}

	public void reverseAdabegiakInprimatu() {
		System.out.println(this.reverseToString());
	}

	public String reverseToString() {
		String result = new String();
		Iterator<T> it = reverseIterator();
		while (it.hasNext()) {
			T elem = it.next();
			result = result + "[" + elem.toString() + "] \n";
		}
		return "SimpleLinkedList " + result;
	}
}
