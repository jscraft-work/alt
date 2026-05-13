import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ApiError, api, toApiError } from "@/lib/api";
import type {
  ApiPaged,
  DisclosureListFilter,
  DisclosureListItem,
  NewsListFilter,
  NewsListItem,
} from "@/lib/api-types";

export const NEWS_LIST_KEY = ["news"] as const;
export const DISCLOSURE_LIST_KEY = ["disclosures"] as const;

function buildQuery(params: Record<string, string | number | null | undefined>) {
  const searchParams = new URLSearchParams();

  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === "") {
      continue;
    }
    searchParams.set(key, String(value));
  }

  const qs = searchParams.toString();
  return qs ? `?${qs}` : "";
}

async function fetchNewsList(
  filter: NewsListFilter,
): Promise<ApiPaged<NewsListItem>> {
  try {
    const res = await api.get<ApiPaged<NewsListItem>>(
      `/news${buildQuery({
        symbolCode: filter.symbolCode,
        strategyInstanceId: filter.strategyInstanceId,
        usefulnessStatus: filter.usefulnessStatus,
        dateFrom: filter.dateFrom,
        dateTo: filter.dateTo,
        q: filter.q,
        page: filter.page,
        size: filter.size,
      })}`,
    );
    return res.data;
  } catch (error) {
    throw toApiError(error);
  }
}

async function fetchDisclosureList(
  filter: DisclosureListFilter,
): Promise<ApiPaged<DisclosureListItem>> {
  try {
    const res = await api.get<ApiPaged<DisclosureListItem>>(
      `/disclosures${buildQuery({
        symbolCode: filter.symbolCode,
        dartCorpCode: filter.dartCorpCode,
        strategyInstanceId: filter.strategyInstanceId,
        dateFrom: filter.dateFrom,
        dateTo: filter.dateTo,
        page: filter.page,
        size: filter.size,
      })}`,
    );
    return res.data;
  } catch (error) {
    throw toApiError(error);
  }
}

export function useNewsList(filter: NewsListFilter) {
  return useQuery<ApiPaged<NewsListItem>, ApiError>({
    queryKey: [...NEWS_LIST_KEY, filter],
    queryFn: () => fetchNewsList(filter),
    placeholderData: keepPreviousData,
  });
}

export function useDisclosureList(filter: DisclosureListFilter) {
  return useQuery<ApiPaged<DisclosureListItem>, ApiError>({
    queryKey: [...DISCLOSURE_LIST_KEY, filter],
    queryFn: () => fetchDisclosureList(filter),
    placeholderData: keepPreviousData,
  });
}
