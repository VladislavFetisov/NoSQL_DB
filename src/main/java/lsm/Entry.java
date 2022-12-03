package lsm;

public interface Entry<D> {
    D key();

    D value();
}
