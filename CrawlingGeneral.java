package com.welgram.crawler;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.welgram.common.HttpClientUtil;
import com.welgram.crawler.general.CrawlingProduct;
import com.welgram.crawler.general.CrawlingTreaty;
import com.welgram.util.Birthday;
import com.welgram.util.InsuranceUtil;

import net.sf.json.JSONObject;

/**
 * @author beangelus
 */
public abstract class CrawlingGeneral extends CrawlingMain {

	protected abstract void doCrawlInsurance(CrawlingProduct product);

	protected void elementWait(String value) {
		try {
			System.out.println(value + " - element 찾는 중...");
			wait = new WebDriverWait(driver, WAIT_TIME);
			wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(value)));
		} catch (Exception e) {
			System.out.println(value + " 요소를 찾을 수 없습니다.");
		}
	}

	protected void switchToWindow(String currentHandle, Set<String> windowId, boolean value) {
		Iterator<String> handles = windowId.iterator();
		// 메인 윈도우 창 확인
		subHandle = null;

		while (handles.hasNext()) {
			subHandle = handles.next();
			if (subHandle.equals(currentHandle)) {
				continue;
			} else {
				// true : 이전 창을 닫지 않음, false : 이전 창을 닫음
				if (!value) {
					driver.close();
				}
				driver.switchTo().window(subHandle);
				wait = new WebDriverWait(driver, WAIT_TIME);
				break;
			}
		}
	}

	@Override
	protected void execute(String productCode, String[] param) {
		boolean result = true;
		JSONObject data = null;

		CrawlingProduct product = new CrawlingProduct();
		try {

			if (param.length == 0) {
				// 크롤링용
				data = (JSONObject) HttpClientUtil.sendGET(URL_API + productCode).get("data");
				HttpClientUtil.sendPUT(URL_API + productCode + "/crawStart", "{}");
			} else {
				// 모니터링용
				data = (JSONObject) HttpClientUtil.sendGET(URL_API + productCode + "/limit/" + param[0]).get("data");
				HttpClientUtil.sendPUT(URL_API + productCode + "/monitorStart", "{}");
			}

			int totalCount = data.getInt("totalCalc");
			int mapperCount = data.getInt("totalMapper");
			int planCount = totalCount / mapperCount;
			int planMasterCount = 1;
			crawlCount = 0;

			String categoryName = data.getJSONObject("product").getString("categoryName");
			String productName = ((JSONObject) data.get("product")).getString("productName");

			@SuppressWarnings("unchecked")
			List<JSONObject> planMasters = (List<JSONObject>) data.get("planMasters");
			for (JSONObject planMaster : planMasters) {

				@SuppressWarnings("unchecked")
				List<JSONObject> planMappers = (List<JSONObject>) planMaster.get("planMappers");

				crawlUrl = planMaster.get("siteUrl").toString();
				System.out.println("planName : " + planMaster.get("planName"));
				System.out.println("siteUrl : " + planMaster.get("siteUrl"));

				// 재배열하기 위하여 planCalcsList 사용
				List<JSONObject> planCalcsList = new ArrayList<JSONObject>();
				for (JSONObject planMapper : planMappers) {
					@SuppressWarnings("unchecked")
					List<JSONObject> caseNumbers = (List<JSONObject>) planMapper.get("planCalcs");
					for (JSONObject caseNumber : caseNumbers) {
						planCalcsList.add(caseNumber);
					}
				}

				if (mapperCount * planCount != totalCount) {
					System.out.println("@@@@@@@ totalCount : " + totalCount);
					System.out.println("@@@@@@@ mapperCount : " + mapperCount);
					System.out.println("@@@@@@@ planCount : " + planCount);

					throw new Exception("상품에 대한 설계정보와 총 가입설계의 갯수가 같지 않습니다.");
				}

				List<CrawlingProduct> planList = new ArrayList<CrawlingProduct>();
				for (int i = 0; i < planCount; i++) {

					product = new CrawlingProduct();
					for (int j = 0; j < planCalcsList.size(); j++) {
						if (i == (j % planCount)) {
							JSONObject jsonObject = planCalcsList.get(j);
							int caseNumber = jsonObject.getInt("planCalcId");
							int age = Integer.valueOf(jsonObject.getString("insAge"));
							
							Birthday birthDay = InsuranceUtil.getBirthday(age);

							JSONObject planMapper = planMappers.get(j / planCount);
							CrawlingTreaty treaty = new CrawlingTreaty();
							product.categoryName = categoryName;
							product.planName = planMaster.getString("planName");
							product.productName = productName;
							product.productCode = productCode;
							product.currentCrawlCount = (++crawlCount) / 11;
							product.totalCrawlCount = planCount;
							product.currentMasterCount = planMasterCount;
							product.totalMasterCount = planMasters.size();
							
							product.setGender(jsonObject.getString("gender").trim().equals("M") ? MALE : FEMALE);
							product.setAge(jsonObject.getString("insAge"));
							product.setInsuName(planMaster.getString("planName"));
							product.setBirth(birthDay.getYear().substring(2, 4) + birthDay.getMonth() + birthDay.getDay());
							product.setFullBirth(birthDay.getYear().substring(0, 4) + birthDay.getMonth() + birthDay.getDay());
							product.setParent_FullBirth(40);
							product.setParent_Birth(40);
							product.setDiscount(planMaster.getString("discount"));
							product.setInsTerm(planMapper.getString("insTermNm"));
							product.setNapTerm(planMapper.getString("napTermNm"));
							product.setNapCycle(planMapper.getString("napCycleNm"));
							product.setAnnAge(planMapper.getString("annuityAgeNm"));
							product.setProductKind(planMapper.getString("productKindNm"));
							product.setPremium(planMapper.getString("assureMoneyNm"));
							product.setProductType(planMapper.getString("productTypeNm"));
							product.setCaseNumber(caseNumber);
							product.setPregnancyWeek(20);
							
							
							treaty.mapperId = planMapper.getString("mapperId");
							treaty.setProductGubun(planMapper.getString("productGubunNm"));
							treaty.setProductKind(planMapper.getString("productKindNm"));
							treaty.setProductType(planMapper.getString("productTypeNm"));
							treaty.setInsTerm(planMapper.getString("insTermNm"));
							treaty.setNapTerm(planMapper.getString("napTermNm"));
							treaty.setNapCycle(planMapper.getString("napCycleNm"));
							treaty.setAnnAge(planMapper.getString("annuityAgeNm"));
							treaty.assureMoney = Integer.parseInt(planMapper.getString("assureMoneyNm"));
							treaty.treatyName = planMapper.getString("productMasterName");
							treaty.planCalcs = caseNumber;
							product.treatyList.add(treaty);
						}
					}
					planList.add(product);
				}

				// 크롤링 실행
				crawlCount = 0;
				for (CrawlingProduct item : planList) {
					try {

						System.out.println("############## 크롤링 진행상태 - PLAN : " + ++crawlCount + " / " + planCount + " ||  PLAN MASTER : " + planMasterCount + " / " + planMasters.size() + " ##############");
						System.out.println(" - 나이        : " + item.age + "세");
						System.out.println(" - 성별 	  : " + (item.getGender() == MALE ? "남자" : "여자"));
						System.out.println(" - 생년월일  : " + item.fullBirth);
						System.out.println("");
						for (CrawlingTreaty treaty : item.treatyList) {
							System.out.println(" - 특약이름 : " + treaty.treatyName);
							System.out.println(" - 상품구분 : " + treaty.productGubun);
							System.out.println(" - 상품종류 : " + treaty.productKind);
							System.out.println(" - 상품형태 : " + treaty.productType);
							System.out.println(" - 가설넘버 : " + treaty.planCalcs);
							System.out.println(" - 보험금    : " + treaty.assureMoney);
							System.out.println(" - 보험기간 : " + treaty.insTerm);
							System.out.println(" - 납입기간 : " + treaty.napTerm);
							System.out.println(" - 납입방법 : " + (treaty.getNapCycle() == "01" ? "월납" : (treaty.getNapCycle() == "02" ? "년납" : "일시납")));
							System.out.println("");
						}

						// 특약개수가 너무 많아 상품등록할 때 특약이 중복으로 등록되는 경우가 많음
						for (int i = 0; i < item.treatyList.size(); i++) {
							String name = item.treatyList.get(i).treatyName;
							for (int j = 0; j < item.treatyList.size(); j++) {
								if (i == j) {
									continue;
								}
								if (name.equals(item.treatyList.get(j).treatyName)) {
									System.out.println("중복으로 등록된 특약명 : " + item.treatyList.get(j).treatyName);
								}
							}
						}

						// 크롤링 실행
						stopWatch.start();
						doCrawlInsurance(item);
						stopWatch.stop();
						item.crawlingTime = (stopWatch.getTime() / 1000.0) + "초";

						int monthlyPremium = 0;
						for (CrawlingTreaty treaty : item.treatyList) {
							System.out.println("     >>> 특약이름   : " + treaty.treatyName);
							System.out.println("     >>> 특약보험료 : " + treaty.monthlyPremium);
							monthlyPremium = monthlyPremium + Integer.parseInt(treaty.monthlyPremium.equals("") ? "0" : treaty.monthlyPremium);
						}
						System.out.println(" >>> 해지환급금 : " + item.returnPremium);
						System.out.println(" >>> 연금수령액 : " + item.annuityPremium);
						System.out.println(" >>> 적립보험료 : " + item.monthlyPremium);
						System.out.println(" >>> 보장보험료 : " + monthlyPremium);

						monthlyPremium = monthlyPremium + item.monthlyPremium;
						System.out.println(" >>> 총보험료   : " + monthlyPremium);

						System.out.println(" >>> 소요시간   : " + item.crawlingTime);
						System.out.println("");

						if (param.length == 0) {
							// 크롤링 결과 전송
							sendResult(item);
							// 크롤링 결과 log
							printLog(item);
						} else {
							comparing(item);
						}

					} catch (Exception e) {
						throw new Exception("Crawling Error : " + e.getMessage());
					} finally {
						stopWatch.reset();
					}
				}
				++planMasterCount;
			}
		} catch (Exception e) {
			result = false;
			product.errorMessage = e.toString();
			if (param.length == 0) {
				HttpClientUtil.sendPUT(URL_API + productCode + "/crawError", "{}");
			} else {
				HttpClientUtil.sendPUT(URL_API + productCode + "/monitorFailure", "{}");
			}
			System.out.println("크롤링 도중 에러 발생하여 종료됨");
			e.printStackTrace();
		} finally {
			// 크롤링 결과 log
			if (result) {
				System.out.println("크롤링 완료!");
				if (param.length == 0) {
					HttpClientUtil.sendPUT(URL_API + productCode + "/crawEnd", "{}");
				} else {
					HttpClientUtil.sendPUT(URL_API + productCode + "/monitorSuccess", "{}");
				}

			}
		}
	}

	private void sendResult(CrawlingProduct product) {

		boolean value = true;
		for (CrawlingTreaty treaty : product.treatyList) {
			Map<String, Object> sendPutParam = new HashMap<String, Object>();
			sendPutParam.put("insMoney", treaty.monthlyPremium); // 보험료

			// 주보험(첫번째 mapper)에만 해지환급금 및 연금수령액 데이터 삽입
			if (value) {
				sendPutParam.put("saveMoney", product.monthlyPremium); // 적립보험료
				sendPutParam.put("expMoney", product.returnPremium); // 해지환급금
				sendPutParam.put("annMoney", product.annuityPremium); // 연금수령액
				sendPutParam.put("errorMsg", product.errorMessage); // 에러 메세지
				value = false;
			} else {
				sendPutParam.put("expMoney", "0"); // 해지환급금
				sendPutParam.put("annMoney", "0"); // 연금수령액
				sendPutParam.put("errorMsg", ""); // 에러 메세지
			}

			HttpClientUtil.sendPUT(URL_API + product.productCode + "/planCalcs/" + treaty.planCalcs, JSONObject.fromObject(sendPutParam).toString());
		}
	}

	private void printLog(CrawlingProduct product) {
		String fileName = product.productCode + ".log";

		try {
			BufferedWriter fw = new BufferedWriter(new FileWriter(fileName, true));

			fw.write("############## 크롤링 진행상태 - PLAN : " + product.currentCrawlCount + " / " + product.totalCrawlCount + " ||  PLAN MASTER : " + product.currentMasterCount + " / " + product.totalMasterCount + " ##############\r\n");
			fw.write(" - 나이      : " + product.age + "\r\n");
			fw.write(" - 성별      : " + product.gender + "\r\n");
			fw.write(" - 생년월일 : " + product.fullBirth + "\r\n");
			fw.newLine();

			for (CrawlingTreaty treaty : product.treatyList) {
				fw.write(" - 특약이름 : " + treaty.treatyName + "\r\n");
				fw.write(" - 상품구분 : " + treaty.productGubun + "\r\n");
				fw.write(" - 상품종류 : " + treaty.productKind + "\r\n");
				fw.write(" - 상품형태 : " + treaty.productType + "\r\n");
				fw.write(" - 가설넘버 : " + treaty.planCalcs + "\r\n");
				fw.write(" - 보험금   : " + treaty.assureMoney + "\r\n");
				fw.write(" - 보험기간 : " + treaty.insTerm + "\r\n");
				fw.write(" - 납입기간 : " + treaty.napTerm + "\r\n");
				fw.write(" - 납입방법 : " + treaty.napCycle + "\r\n");
				fw.newLine();
			}

			fw.write(" >>> 해지환급금 : " + product.returnPremium + "\r\n");
			fw.write(" >>> 연금수령액 : " + product.annuityPremium + "\r\n");
			fw.write(" >>> 총보험료   : " + product.monthlyPremium + "\r\n");

			for (CrawlingTreaty treaty : product.treatyList) {
				fw.write("     >>> 특약이름   : " + treaty.treatyName + "\r\n");
				fw.write("     >>> 특약보험료 : " + treaty.monthlyPremium + "\r\n");
			}

			fw.write(" >>> 소요시간   : " + product.crawlingTime);
			fw.newLine();
			fw.newLine();
			fw.flush();
			fw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
