/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package org.compiere.process;

import org.adempiere.ad.security.IUserRolePermissionsDAO;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;
import org.compiere.model.I_AD_Role_OrgAccess;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_M_Warehouse;
import org.compiere.model.MBPartner;
import org.compiere.model.MLocator;
import org.compiere.model.MOrg;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MWarehouse;
import org.compiere.util.AdempiereUserError;

import de.metas.process.JavaProcess;
import de.metas.process.ProcessInfoParameter;

/**
 *	Link Business Partner to Organization.
 *	Either to existing or create new one
 *
 *  @author Jorg Janke
 *  @version $Id: BPartnerOrgLink.java,v 1.2 2006/07/30 00:51:02 jjanke Exp $
 */
public class BPartnerOrgLink extends JavaProcess
{
	private final transient IUserRolePermissionsDAO permissionsDAO = Services.get(IUserRolePermissionsDAO.class);

	/**	Existing Org			*/
	private int			p_AD_Org_ID;
	/** Info for New Org		*/
	private int			p_AD_OrgType_ID;
	/** Business Partner		*/
	private int			p_C_BPartner_ID;
	/** Role					*/
	private int			p_AD_Role_ID;

	/**
	 * Business partner location (03084)
	 */
	private int  p_C_BPartner_Location_ID;


	/**
	 *  Prepare - e.g., get Parameters.
	 */
	@Override
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParametersAsArray();
		for (ProcessInfoParameter element : para)
		{
			String name = element.getParameterName();
			if (element.getParameter() == null)
				;
			else if (name.equals("AD_Org_ID"))
				p_AD_Org_ID = element.getParameterAsInt();
			else if (name.equals("AD_OrgType_ID"))
				p_AD_OrgType_ID = element.getParameterAsInt();
			else if (name.equals("AD_Role_ID"))
				p_AD_Role_ID = element.getParameterAsInt();
			// 03084: get the C_BPartner_Location parameter
			else if (name.equals("C_BPartner_Location_ID"))
				p_C_BPartner_Location_ID = element.getParameterAsInt();
			else
				log.error("prepare - Unknown Parameter: " + name);
		}
		p_C_BPartner_ID = getRecord_ID();
	}	//	prepare

	/**
	 *  Perform process.
	 *  @return Message (text with variables)
	 *  @throws Exception if not successful
	 */
	@Override
	protected String doIt() throws Exception
	{
		log.info("C_BPartner_ID=" + p_C_BPartner_ID
			+ ", AD_Org_ID=" + p_AD_Org_ID
			+ ", AD_OrgType_ID=" + p_AD_OrgType_ID
			+ ", AD_Role_ID=" + p_AD_Role_ID
			// 03084: add the C_BPartner_Location as parameter
			+ ", C_BPartner_Location_ID=" + p_C_BPartner_Location_ID);
		if (p_C_BPartner_ID == 0)
			throw new AdempiereUserError ("No Business Partner ID");
		MBPartner bp = new MBPartner (getCtx(), p_C_BPartner_ID, get_TrxName());
		if (bp.get_ID() == 0)
			throw new AdempiereUserError ("Business Partner not found - C_BPartner_ID=" + p_C_BPartner_ID);

		//	Create Org
		boolean newOrg = p_AD_Org_ID == 0;
		MOrg org = new MOrg (getCtx(), p_AD_Org_ID, get_TrxName());
		if (newOrg)
		{
			org.setValue (bp.getValue());
			org.setName (bp.getName());
			org.setDescription (bp.getDescription());
			if (!org.save())
				throw new Exception ("Organization not saved");
		}
		else	//	check if linked to already
		{
			int C_BPartner_ID = org.getLinkedC_BPartner_ID(get_TrxName());
			if (C_BPartner_ID > 0)
				throw new IllegalArgumentException ("Organization '" + org.getName()
					+ "' already linked (to C_BPartner_ID=" + C_BPartner_ID + ")");
		}
		p_AD_Org_ID = org.getAD_Org_ID();

		//	Update Org Info
		MOrgInfo oInfo = org.getInfo();
		oInfo.setAD_OrgType_ID (p_AD_OrgType_ID);

		// metas: 03084: We are no longer setting the location to AD_OrgInfo.
		// Location is contained in linked bpartner's location
		//if (newOrg)
		//	oInfo.setC_Location_ID(C_Location_ID);

		//	Create Warehouse
		MWarehouse wh = null;
		if (!newOrg)
		{
			MWarehouse[] whs = MWarehouse.getForOrg(getCtx(), p_AD_Org_ID);
			if (whs != null && whs.length > 0)
				wh = whs[0];	//	pick first
		}
		//	New Warehouse
		if (wh == null)
		{
			wh = new MWarehouse(org);
			configureWarehouse(wh, bp, p_C_BPartner_Location_ID); // metas: 03084
			if (!wh.save(get_TrxName()))
				throw new Exception ("Warehouse not saved");
		}
		//	Create Locator

		MLocator mLoc = wh.getDefaultLocator();
		if (mLoc == null)
		{
			mLoc = new MLocator (wh, "Standard");
			mLoc.setIsDefault(true);
			mLoc.save(get_TrxName());
		}

		//	Update/Save Org Info
		oInfo.setM_Warehouse_ID(wh.getM_Warehouse_ID());
		if (!oInfo.save(get_TrxName()))
			throw new Exception ("Organization Info not saved");

		//	Update BPartner
		bp.setAD_OrgBP_ID(p_AD_Org_ID);
		if (bp.getAD_Org_ID() != 0)
			bp.setClientOrg(bp.getAD_Client_ID(), 0);	//	Shared BPartner

		//	Save BP
		if (!bp.save())
			throw new Exception ("Business Partner not updated");

		//
		//	Limit to specific Role
		if (p_AD_Role_ID > 0)
		{
			boolean found = false;
			//	delete all accesses except the specific
			for (final I_AD_Role_OrgAccess orgAccess : permissionsDAO.retrieveRoleOrgAccessRecordsForOrg(p_AD_Org_ID))
			{
				if (orgAccess.getAD_Role_ID() == p_AD_Role_ID)
				{
					found = true;
				}
				else
				{
					InterfaceWrapperHelper.delete(orgAccess);
				}
			}
			//	create access
			if (!found)
			{
				permissionsDAO.createOrgAccess(p_AD_Role_ID, org.getAD_Org_ID());
			}
		}

		//	Reset Client Role
		// FIXME: MRole.getDefault(getCtx(), true);

		return "Business Partner - Organization Link created";
	}	//	doIt

	/**
	 * Configure/Update Warehouse from given linked BPartner
	 *
	 * @param warehouse
	 * @param bpartner
	 * @task http://dewiki908/mediawiki/index.php/03084:_Move_Org-Infos_to_related_BPartners_%282012080310000055%29
	 */
	private void configureWarehouse(final I_M_Warehouse warehouse, final I_C_BPartner bpartner, final int bPartnerLocationID)
	{
		warehouse.setC_BPartner_Location_ID(bPartnerLocationID);
	}
}	//	BPartnerOrgLink
