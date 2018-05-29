package org.brewchain.account.doublyll;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.brewchain.account.core.WaitBlockHashMapDB;
import org.brewchain.account.util.ALock;
import org.brewchain.account.util.FastByteComparisons;
import org.fc.brewchain.bcapi.EncAPI;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import onight.osgi.annotation.NActorProvider;
import onight.tfw.ntrans.api.ActorService;
import onight.tfw.ntrans.api.annotation.ActorRequire;

@NActorProvider
@Provides(specifications = { ActorService.class }, strategy = "SINGLETON")
@Instantiate(name = "Block_Cache_DLL")
@Slf4j
@Data
public class DoubleLinkedList implements ActorService {
	@ActorRequire(name = "bc_encoder", scope = "global")
	EncAPI encApi;

	private Node first = null;
	private Node last = null;
	private int count = 0;

	private ReadWriteLock rwLock = new ReentrantReadWriteLock();
	private ALock readLock = new ALock(rwLock.readLock());
	private ALock writeLock = new ALock(rwLock.writeLock());

	public DoubleLinkedList() {
		first = null;
		last = null;
		count = 0;
	}

	public void insertFirst(byte[] elem, int number) {
		try (ALock l = writeLock.lock()) {
			Node oNode = new Node(elem, number);
			if (first == null) {
				first = oNode;
			} else {
				if (count == 1) {
					oNode.next = first;
					first.prev = oNode;
					last = first;
					first = oNode;
				} else {
					oNode.next = first;
					first.prev = oNode;
					first = oNode;
				}
			}
			count++;
		}
	}

	public void insertLast(byte[] elem, int number) {
		try (ALock l = writeLock.lock()) {
			Node oNode = new Node(elem, number);
			if (last == null) {
				last = oNode;
			} else {
				oNode.prev = last;
				last.next = oNode;
				last = oNode;
			}

			count++;
		}
	}

	public boolean insertAfter(byte[] elem, int number, byte[] target) {
		try (ALock l = writeLock.lock()) {
			Node data = new Node(elem, number);
			Node cur = last == null ? first : last;

			while (cur != null) {
				if (FastByteComparisons.equal(cur.data, target)) {
					if (cur == first && cur.prev == null && last == null) {
						data.prev = cur;
						last = data;
						cur.next = last;
						count++;
						return true;
					} else {
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
				}
				cur = cur.prev;
			}
		}
		return false;
	}

	public byte[] removeFirst() {
		try (ALock l = writeLock.lock()) {
			byte[] o = first.data;
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
	}

	public byte[] removeLast() {
		try (ALock l = writeLock.lock()) {
			byte[] o = last.data;
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
	}

	public byte[] remove(byte[] elem) {
		try (ALock l = writeLock.lock()) {
			byte[] o = null;
			Node egungoa = first;

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
	}

	public byte[] first() {
		try (ALock l = readLock.lock()) {
			if (isEmpty())
				return null;
			else
				return first.data;
		}
	}

	public byte[] last() {
		try (ALock l = readLock.lock()) {
			if (isEmpty())
				return null;
			else {
				if (last != null) {
					return last.data;
				} else if (count == 1) {
					return first.data;
				} else {
					return null;
				}
			}
		}
	}

	public boolean contains(byte[] elem) {
		try (ALock l = readLock.lock()) {
			if (isEmpty())
				return false;

			Node current = first;

			while ((current != null) && !elem.equals(current.data))
				current = current.next;
			if (current == null)
				return false;
			else
				return elem.equals(current.data);
		}
	}

	public byte[] find(byte[] pElementua) {
		try (ALock l = readLock.lock()) {
			byte[] elementua = null;

			Iterator it = iterator();
			boolean topatua = false;

			while (it.hasNext() && !topatua) {
				elementua = (byte[]) it.next();
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
	}

	public boolean isEmpty() {
		return first == null;
	}

	public void clear() {
		first = null;
		last = null;
		count = 0;
	}

	public int size() {
		return count;
	}

	public Iterator iterator() {
		return new ListIterator();
	}

	public Iterator reverseIterator() {
		return new ReverseListIterator();
	}

	private class ListIterator implements Iterator {

		private Node egungoElementua = first;

		@Override
		public boolean hasNext() {
			return (egungoElementua != null) && egungoElementua.next != null;
		}

		@Override
		public Node next() {
			try (ALock l = readLock.lock()) {
				if (!hasNext())
					throw new NoSuchElementException();
				Node t = egungoElementua;
				egungoElementua = egungoElementua.next;
				return t;
			}
		}

		@Override
		public void remove() {
			// TODO Auto-generated method stub

		}

	}

	private class ReverseListIterator implements Iterator {

		private Node egungoElementua = last;

		@Override
		public boolean hasNext() {
			return (egungoElementua != null);
		}

		@Override
		public Node next() {
			try (ALock l = readLock.lock()) {
				if (!hasNext())
					throw new NoSuchElementException();
				Node t = egungoElementua;
				egungoElementua = egungoElementua.prev;
				return t;
			}
		}

		@Override
		public void remove() {
			// TODO Auto-generated method stub

		}

	}

	public void adabegiakInprimatu() {
		System.out.println(this.formatString());
	}

	public String formatString() {
		String result = new String();
		Iterator it = iterator();
		while (it.hasNext()) {
			byte[] elem = (byte[]) it.next();
			result = result + "[" + encApi.hexEnc((byte[]) elem) + "] \n";
		}
		return result;
	}

	public void reverseAdabegiakInprimatu() {
		System.out.println(this.reverseFormatString());
	}

	public String reverseFormatString() {
		String result = new String();
		Iterator it = reverseIterator();
		while (it.hasNext()) {
			byte[] elem = (byte[]) it.next();
			result = result + "[" + elem.toString() + "] \n";
		}
		return "SimpleLinkedList " + result;
	}
}
