import { describe, expect, it } from "vitest";
import { retailerGroups } from "./retailers";

describe("retailerGroups", () => {
  it("files a branch under the plain retailer name once that name appears", () => {
    const groups = retailerGroups(["Dishoom Kings Cross", "Dishoom", "Dishoom Shoreditch"]);
    expect(groups.get("Dishoom Kings Cross")).toBe("Dishoom");
    expect(groups.get("Dishoom Shoreditch")).toBe("Dishoom");
    expect(groups.get("Dishoom")).toBe("Dishoom");
  });

  it("leaves a branch alone when the plain name never turns up", () => {
    expect(retailerGroups(["Dishoom Kings Cross"]).get("Dishoom Kings Cross")).toBe("Dishoom Kings Cross");
  });

  it("only matches whole leading words", () => {
    const groups = retailerGroups(["British Gas", "British Airways", "Tesco", "Tescos Garage"]);
    expect(groups.get("British Gas")).toBe("British Gas");
    expect(groups.get("British Airways")).toBe("British Airways");
    expect(groups.get("Tescos Garage")).toBe("Tescos Garage");
  });

  it("ignores case and apostrophes", () => {
    expect(retailerGroups(["Sainsburys", "SAINSBURY'S Local"]).get("SAINSBURY'S Local")).toBe("Sainsburys");
  });
});
