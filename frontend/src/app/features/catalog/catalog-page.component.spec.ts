import {TestBed} from '@angular/core/testing';
import {provideRouter} from '@angular/router';
import {of, throwError} from 'rxjs';
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {CatalogService} from '../../core/catalog/catalog.service';
import {CatalogPageComponent} from './catalog-page.component';

const emptyPage = {
  content: [], totalElements: 0, totalPages: 0, number: 0,
  size: 12, first: true, last: true
};

describe('CatalogPageComponent', () => {
  let catalog: {list: ReturnType<typeof vi.fn>};

  beforeEach(() => {
    catalog = {list: vi.fn().mockReturnValue(of({data: emptyPage}))};
    TestBed.configureTestingModule({
      imports: [CatalogPageComponent],
      providers: [provideRouter([]), {provide: CatalogService, useValue: catalog}]
    });
  });

  afterEach(() => TestBed.resetTestingModule());

  it('loads the first catalogue page on creation', () => {
    const fixture = TestBed.createComponent(CatalogPageComponent);
    fixture.detectChanges();

    expect(catalog.list).toHaveBeenCalledWith(0, '');
    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.componentInstance.total()).toBe(0);
  });

  it('trims search text and returns to the first page', () => {
    const fixture = TestBed.createComponent(CatalogPageComponent);
    const component = fixture.componentInstance;
    component.page.set(3);
    component.query = '  desk lamp  ';

    component.searchProducts();

    expect(catalog.list).toHaveBeenLastCalledWith(0, 'desk lamp');
    expect(component.page()).toBe(0);
    expect(component.query).toBe('desk lamp');
  });

  it('clears the search and returns to the first page', () => {
    const fixture = TestBed.createComponent(CatalogPageComponent);
    const component = fixture.componentInstance;
    component.page.set(2);
    component.query = 'camera';

    component.clearSearch();

    expect(catalog.list).toHaveBeenLastCalledWith(0, '');
    expect(component.page()).toBe(0);
    expect(component.query).toBe('');
  });

  it('shows a recoverable error when the catalogue request fails', () => {
    catalog.list.mockReturnValue(throwError(() => new Error('network')));
    const fixture = TestBed.createComponent(CatalogPageComponent);

    expect(fixture.componentInstance.loading()).toBe(false);
    expect(fixture.componentInstance.error()).toContain('could not load');
  });

  it('ignores an older response after a newer catalogue request', () => {
    let firstNext: ((value: {data: typeof emptyPage}) => void) | undefined;
    catalog.list.mockImplementationOnce(() => ({
      subscribe: (observer: {next: (value: {data: typeof emptyPage}) => void}) => { firstNext = observer.next; return {unsubscribe: () => undefined}; }
    })).mockReturnValueOnce(of({data: {...emptyPage, totalElements: 7}}));

    const fixture = TestBed.createComponent(CatalogPageComponent);
    const component = fixture.componentInstance;
    component.query = 'new';
    component.searchProducts();
    firstNext?.({data: {...emptyPage, totalElements: 99}});

    expect(component.total()).toBe(7);
  });
});
